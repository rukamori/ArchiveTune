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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.auth.MenuProfile
import moe.rukamori.archivetune.auth.ObserveMenuProfileUseCase
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface MenuProfileState {
    data object Loading : MenuProfileState

    data class Success(val profile: MenuProfile) : MenuProfileState

    data object Empty : MenuProfileState

    data class Error(@param:StringRes val messageResId: Int) : MenuProfileState
}

enum class MainMenuDestination(val route: String) {
    Profile("settings/account"),
    NewRelease("new_release"),
    News("news"),
    History("history"),
    Settings("settings"),
}

@HiltViewModel
class MainMenuViewModel
    @Inject
    constructor(
        observeMenuProfile: ObserveMenuProfileUseCase,
    ) : ViewModel() {
        private val _expanded = MutableStateFlow(false)
        val expanded = _expanded.asStateFlow()

        val profileState: StateFlow<MenuProfileState> =
            observeMenuProfile()
                .map<MenuProfile?, MenuProfileState> { profile ->
                    if (profile != null) MenuProfileState.Success(profile) else MenuProfileState.Empty
                }.catch {
                    Timber.w("Unable to observe overflow menu account")
                    emit(MenuProfileState.Error(R.string.error_unknown))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MenuProfileState.Loading)

        private val navigation = Channel<MainMenuDestination>(Channel.BUFFERED)
        val destinations = navigation.receiveAsFlow()

        fun toggle() {
            _expanded.value = !_expanded.value
        }

        fun dismiss() {
            _expanded.value = false
        }

        fun select(destination: MainMenuDestination) {
            if (!_expanded.value) return
            dismiss()
            viewModelScope.launch {
                navigation.send(destination)
            }
        }
    }
