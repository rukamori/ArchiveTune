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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.auth.CompleteYouTubeLoginUseCase
import moe.rukamori.archivetune.auth.GenerateYouTubePoTokensUseCase
import moe.rukamori.archivetune.auth.MissingYouTubeDataSyncIdException
import moe.rukamori.archivetune.auth.SaveYouTubePoTokensUseCase
import moe.rukamori.archivetune.auth.UpdateYouTubeLoginContextUseCase
import moe.rukamori.archivetune.auth.UpdateYouTubeLoginSessionChannelUseCase
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import timber.log.Timber
import javax.inject.Inject

sealed interface LoginScreenState {
    data object Loading : LoginScreenState

    data class Success(
        val account: LoginAccountUiModel,
    ) : LoginScreenState

    data class ChannelSelection(
        val channels: List<AccountChannelUiModel>,
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

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val completeYouTubeLogin: CompleteYouTubeLoginUseCase,
        private val updateYouTubeLoginContext: UpdateYouTubeLoginContextUseCase,
        private val generateYouTubePoTokens: GenerateYouTubePoTokensUseCase,
        private val saveYouTubePoTokens: SaveYouTubePoTokensUseCase,
        private val updateYouTubeLoginSessionChannel: UpdateYouTubeLoginSessionChannelUseCase,
    ) : ViewModel() {
        private val _screenState = MutableStateFlow<LoginScreenState>(LoginScreenState.Empty)
        val screenState: StateFlow<LoginScreenState> = _screenState.asStateFlow()

        private var latestVisitorData: String? = null
        private var latestDataSyncId: String? = null
        private var loginJob: Job? = null
        private var activeCookie: String? = null
        private var completedCookie: String? = null
        private var latestGvsPoToken: String? = null

        fun onVisitorDataExtracted(visitorData: String?) {
            val normalized = visitorData.normalizeAuthValue() ?: return
            latestVisitorData = normalized
            viewModelScope.launch {
                updateYouTubeLoginContext(visitorData = normalized)
            }
        }

        fun onDataSyncIdExtracted(dataSyncId: String?) {
            val normalized = dataSyncId.normalizeDataSyncId() ?: return
            if (latestDataSyncId == normalized) return

            val currentState = _screenState.value
            if (currentState is LoginScreenState.Success && currentState.account.dataSyncId != normalized) return

            latestDataSyncId = normalized
            viewModelScope.launch {
                updateYouTubeLoginContext(dataSyncId = normalized)
            }
            activeCookie?.let { startLogin(it, replaceActive = true) }
        }

        fun onGvsPoTokenExtracted(gvsPoToken: String?) {
            val normalized = gvsPoToken.normalizeAuthValue() ?: return
            latestGvsPoToken = normalized
        }

        fun onCookiesCaptured(cookie: String?) {
            val normalizedCookie = cookie.normalizeAuthValue() ?: return
            startLogin(normalizedCookie, replaceActive = false)
        }

        private fun startLogin(
            normalizedCookie: String,
            replaceActive: Boolean,
        ) {
            if (completedCookie == normalizedCookie) return
            if (!replaceActive && loginJob?.isActive == true && activeCookie == normalizedCookie) return

            if (activeCookie != null && (activeCookie != normalizedCookie || replaceActive)) {
                latestGvsPoToken = null
            }
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
                        persistPoTokens(
                            sessionId = session.authState.sessionId,
                            visitorData = session.authState.visitorData,
                        )
                        val accountChannels = YouTube.accountChannels().getOrNull()?.map { channel ->
                            AccountChannelUiModel(
                                name = channel.name,
                                byline = channel.byline.orEmpty(),
                                channelHandle = channel.channelHandle.orEmpty(),
                                thumbnailUrl = channel.thumbnailUrl.orEmpty(),
                                dataSyncId = channel.dataSyncId,
                                isSelected = channel.isSelected,
                            )
                        }?.takeIf { it.size > 1 }

                        if (accountChannels != null) {
                            _screenState.value = LoginScreenState.ChannelSelection(accountChannels)
                        } else {
                            _screenState.value =
                                LoginScreenState.Success(
                                    LoginAccountUiModel(
                                        name = session.accountName,
                                        email = session.accountEmail,
                                        channelHandle = session.accountChannelHandle,
                                        dataSyncId = session.authState.dataSyncId.orEmpty(),
                                    ),
                                )
                        }
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

        fun selectChannel(channel: AccountChannelUiModel) {
            viewModelScope.launch {
                updateYouTubeLoginSessionChannel(
                    dataSyncId = channel.dataSyncId,
                    accountName = channel.name,
                    accountEmail = channel.byline,
                    accountChannelHandle = channel.channelHandle,
                )
                _screenState.value =
                    LoginScreenState.Success(
                        LoginAccountUiModel(
                            name = channel.name,
                            email = channel.byline,
                            channelHandle = channel.channelHandle,
                            dataSyncId = channel.dataSyncId,
                        ),
                    )
            }
        }

        private suspend fun persistPoTokens(
            sessionId: String?,
            visitorData: String?,
        ) {
            val resolvedSessionId = sessionId ?: return
            val generatedTokens =
                try {
                    generateYouTubePoTokens(resolvedSessionId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.w(throwable, "Failed to generate YouTube PO tokens during login")
                    null
            }
            val gvsToken = generatedTokens?.gvsToken ?: latestGvsPoToken

            try {
                saveYouTubePoTokens(
                    gvsToken = gvsToken,
                    visitorData = visitorData,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Timber.w(throwable, "Failed to persist YouTube PO tokens after login")
            }
        }
    }

private fun String?.normalizeAuthValue(): String? {
    val trimmed = this?.trim()
    return trimmed?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}

private fun String?.normalizeDataSyncId(): String? = PlaybackAuthState(dataSyncId = this).normalized().dataSyncId
