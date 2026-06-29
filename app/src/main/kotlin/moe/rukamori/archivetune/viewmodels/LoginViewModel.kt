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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.auth.CompleteYouTubeLoginUseCase
import moe.rukamori.archivetune.auth.MissingYouTubeDataSyncIdException
import moe.rukamori.archivetune.auth.SaveYouTubePoTokenUseCase
import moe.rukamori.archivetune.auth.UpdateYouTubeLoginContextUseCase
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import timber.log.Timber
import javax.inject.Inject

sealed interface LoginScreenState {
    data object Loading : LoginScreenState

    data class Success(
        val account: LoginAccountUiModel,
    ) : LoginScreenState

    data object Empty : LoginScreenState

    data class Error(
        val error: LoginError,
    ) : LoginScreenState
}

@Immutable
data class LoginAccountUiModel(
    val name: String,
    val email: String,
    val channelHandle: String,
    val dataSyncId: String,
)

enum class LoginError {
    MissingDataSyncId,
    LoginFailed,
}

sealed interface LoginEvent {
    data object Completed : LoginEvent
}

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val completeYouTubeLogin: CompleteYouTubeLoginUseCase,
        private val updateYouTubeLoginContext: UpdateYouTubeLoginContextUseCase,
        private val saveYouTubePoToken: SaveYouTubePoTokenUseCase,
    ) : ViewModel() {
        private val _screenState = MutableStateFlow<LoginScreenState>(LoginScreenState.Empty)
        val screenState: StateFlow<LoginScreenState> = _screenState.asStateFlow()

        private val _events = Channel<LoginEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        private var latestVisitorData: String? = null
        private var latestDataSyncId: String? = null
        private var loginJob: Job? = null
        private var activeCookie: String? = null
        private var completedCookie: String? = null

        fun onVisitorDataExtracted(visitorData: String?) {
            val normalized = visitorData.normalizeAuthValue() ?: return
            latestVisitorData = normalized
            viewModelScope.launch {
                updateYouTubeLoginContext(visitorData = normalized)
            }
        }

        fun onDataSyncIdExtracted(dataSyncId: String?) {
            val normalized = dataSyncId.normalizeDataSyncId() ?: return
            val currentState = _screenState.value
            if (currentState is LoginScreenState.Success && currentState.account.dataSyncId != normalized) return

            latestDataSyncId = normalized
            viewModelScope.launch {
                updateYouTubeLoginContext(dataSyncId = normalized)
            }
            if (currentState is LoginScreenState.Error && currentState.error == LoginError.MissingDataSyncId) {
                onCookiesCaptured(activeCookie)
            }
        }

        fun onPoTokenExtracted(poToken: String?) {
            val normalized = poToken.normalizeAuthValue() ?: return
            viewModelScope.launch {
                saveYouTubePoToken(normalized)
            }
        }

        fun onCookiesCaptured(cookie: String?) {
            val normalizedCookie = cookie.normalizeAuthValue() ?: return
            if (completedCookie == normalizedCookie) return
            if (loginJob?.isActive == true && activeCookie == normalizedCookie) return

            activeCookie = normalizedCookie
            loginJob?.cancel()
            loginJob =
                viewModelScope.launch {
                    _screenState.value = LoginScreenState.Loading
                    completeYouTubeLogin(
                        cookie = normalizedCookie,
                        visitorData = latestVisitorData,
                        dataSyncId = latestDataSyncId,
                    ).onSuccess { session ->
                        completedCookie = normalizedCookie
                        latestVisitorData = session.authState.visitorData
                        latestDataSyncId = session.authState.dataSyncId
                        _screenState.value =
                            LoginScreenState.Success(
                                LoginAccountUiModel(
                                    name = session.accountName,
                                    email = session.accountEmail,
                                    channelHandle = session.accountChannelHandle,
                                    dataSyncId = session.authState.dataSyncId.orEmpty(),
                                ),
                            )
                        _events.send(LoginEvent.Completed)
                    }.onFailure { throwable ->
                        Timber.e(throwable, "Failed to complete YouTube login")
                        _screenState.value =
                            LoginScreenState.Error(
                                if (throwable is MissingYouTubeDataSyncIdException) {
                                    LoginError.MissingDataSyncId
                                } else {
                                    LoginError.LoginFailed
                                },
                            )
                    }
                }
        }
    }

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? = PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
