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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.cipher.CipherSettingsDomainState
import moe.rukamori.archivetune.cipher.ManualCipherRefreshResult
import moe.rukamori.archivetune.cipher.ObserveCipherSettingsUseCase
import moe.rukamori.archivetune.cipher.RefreshCipherUseCase
import moe.rukamori.archivetune.morideobfuscator.CipherRuntimeStatus
import moe.rukamori.archivetune.morideobfuscator.MORI_CIPHER_REFRESH_INTERVAL_MILLIS
import moe.rukamori.archivetune.utils.reportException
import java.util.Locale
import javax.inject.Inject

sealed interface ChiperSettingsUiState {
    data class Loading(
        val progressPercent: Int,
    ) : ChiperSettingsUiState

    data class Success(
        val data: ChiperSettingsUiData,
    ) : ChiperSettingsUiState

    data object Empty : ChiperSettingsUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : ChiperSettingsUiState
}

@Immutable
data class ChiperSettingsUiData(
    val status: CipherRuntimeStatus,
    val playerId: String,
    val lastUpdatedMillis: Long,
    val countdown: String,
    val intervalProgress: Float,
    val remainingManualRefreshes: Int,
    val rateLimitCountdown: String?,
    val isRefreshing: Boolean,
)

data class ChiperSettingsSnackbarEvent(
    @StringRes val messageRes: Int,
)

@HiltViewModel
class ChiperSettingsViewModel
    @Inject
    constructor(
        observeCipherSettings: ObserveCipherSettingsUseCase,
        private val refreshCipher: RefreshCipherUseCase,
    ) : ViewModel() {
        private val mutableState =
            MutableStateFlow<ChiperSettingsUiState>(
                ChiperSettingsUiState.Loading(progressPercent = 0),
            )
        val state: StateFlow<ChiperSettingsUiState> = mutableState.asStateFlow()

        private val mutableEvents = MutableSharedFlow<ChiperSettingsSnackbarEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<ChiperSettingsSnackbarEvent> = mutableEvents.asSharedFlow()

        private var latestDomainState: CipherSettingsDomainState? = null
        private var refreshJob: Job? = null

        init {
            viewModelScope.launch {
                observeCipherSettings().collect { domain ->
                    latestDomainState = domain
                    mutableState.value = domain.toUiState()
                }
            }
        }

        fun refresh() {
            if (refreshJob?.isActive == true) return
            if (latestDomainState == null) return
            refreshJob =
                viewModelScope.launch {
                    try {
                        val refreshResult =
                            refreshCipher()
                        when (refreshResult) {
                            ManualCipherRefreshResult.Updated -> {
                                mutableEvents.emit(ChiperSettingsSnackbarEvent(R.string.mori_cipher_refresh_success))
                            }

                            is ManualCipherRefreshResult.RateLimited -> {
                                mutableEvents.emit(ChiperSettingsSnackbarEvent(R.string.mori_cipher_rate_limited))
                            }

                            is ManualCipherRefreshResult.Failed -> {
                                reportException(refreshResult.cause)
                                mutableEvents.emit(ChiperSettingsSnackbarEvent(R.string.mori_cipher_refresh_failed))
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } finally {
                        refreshJob = null
                    }
                }
        }

        private fun CipherSettingsDomainState.toUiState(): ChiperSettingsUiState {
            val runtime = runtime
            val lastUpdated = runtime.lastSuccessfulRefreshMillis
            val nextRefresh = runtime.nextRefreshAtMillis
            val playerId = runtime.playerId
            if (runtime.status == CipherRuntimeStatus.REFRESHING && lastUpdated == null) {
                return ChiperSettingsUiState.Loading(
                    progressPercent = runtime.refreshProgressPercent?.coerceIn(0, 100) ?: 0,
                )
            }
            if (lastUpdated == null || nextRefresh == null || playerId.isNullOrBlank()) {
                return if (runtime.lastFailure == null) {
                    ChiperSettingsUiState.Empty
                } else {
                    ChiperSettingsUiState.Error(R.string.mori_cipher_load_failed)
                }
            }

            val remaining = (nextRefresh - nowMillis).coerceAtLeast(0L)
            val elapsed = (MORI_CIPHER_REFRESH_INTERVAL_MILLIS - remaining).coerceIn(0L, MORI_CIPHER_REFRESH_INTERVAL_MILLIS)
            val rateLimitRemaining =
                rateLimitResetsAtMillis
                    ?.minus(nowMillis)
                    ?.coerceAtLeast(0L)
            return ChiperSettingsUiState.Success(
                ChiperSettingsUiData(
                    status = runtime.status,
                    playerId = playerId,
                    lastUpdatedMillis = lastUpdated,
                    countdown = remaining.toCountdown(),
                    intervalProgress = elapsed.toFloat() / MORI_CIPHER_REFRESH_INTERVAL_MILLIS.toFloat(),
                    remainingManualRefreshes = remainingRefreshes,
                    rateLimitCountdown = rateLimitRemaining?.toCountdown(),
                    isRefreshing = runtime.status == CipherRuntimeStatus.REFRESHING,
                ),
            )
        }

        private fun Long.toCountdown(): String {
            val totalSeconds = this / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
