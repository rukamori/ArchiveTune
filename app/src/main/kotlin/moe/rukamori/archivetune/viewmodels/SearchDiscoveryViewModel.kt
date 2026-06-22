/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.search.LoadSearchDiscoveryUseCase
import moe.rukamori.archivetune.search.SearchDiscoveryUiModel
import javax.inject.Inject

sealed interface SearchDiscoveryScreenState {
    data object Loading : SearchDiscoveryScreenState

    data class Success(
        val data: SearchDiscoveryUiModel,
    ) : SearchDiscoveryScreenState

    data object Empty : SearchDiscoveryScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : SearchDiscoveryScreenState
}

enum class SearchDiscoveryTab {
    EXPLORE,
    SUGGESTIONS,
}

@HiltViewModel
class SearchDiscoveryViewModel
    @Inject
    constructor(
        private val loadSearchDiscovery: LoadSearchDiscoveryUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<SearchDiscoveryScreenState>(SearchDiscoveryScreenState.Loading)
        val state: StateFlow<SearchDiscoveryScreenState> = _state.asStateFlow()

        private val _selectedTab = MutableStateFlow(SearchDiscoveryTab.EXPLORE)
        val selectedTab: StateFlow<SearchDiscoveryTab> = _selectedTab.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun selectTab(tab: SearchDiscoveryTab) {
            _selectedTab.value = tab
        }

        fun retry() {
            load(force = true)
        }

        private fun load(force: Boolean = false) {
            if (!force && loadJob?.isActive == true) return
            loadJob?.cancel()
            _state.value = SearchDiscoveryScreenState.Loading
            loadJob =
                viewModelScope.launch {
                    _state.value =
                        try {
                            loadSearchDiscovery()
                                .fold(
                                    onSuccess = { data ->
                                        if (data.isEmpty) {
                                            SearchDiscoveryScreenState.Empty
                                        } else {
                                            SearchDiscoveryScreenState.Success(data)
                                        }
                                    },
                                    onFailure = {
                                        SearchDiscoveryScreenState.Error(R.string.error_unknown)
                                    },
                                )
                        } catch (throwable: Throwable) {
                            if (throwable is CancellationException) throw throwable
                            SearchDiscoveryScreenState.Error(R.string.error_unknown)
                        }
                }
        }
    }
