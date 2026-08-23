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
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YT_DLP_UPDATE_INTERVAL_MILLIS
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpRuntimeStatus
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.ytdlp.CheckForYtDlpUpdateUseCase
import moe.rukamori.archivetune.ytdlp.ManualYtDlpUpdateResult
import moe.rukamori.archivetune.ytdlp.ObserveYtDlpSettingsUseCase
import moe.rukamori.archivetune.ytdlp.YtDlpSettingsDomainState
import java.util.Locale
import javax.inject.Inject

sealed interface YtDlpSettingsUiState {
    data object Loading : YtDlpSettingsUiState

    data class Success(
        val data: YtDlpSettingsUiData,
    ) : YtDlpSettingsUiState

    data object Empty : YtDlpSettingsUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : YtDlpSettingsUiState
}

@Immutable
data class YtDlpSettingsUiData(
    val status: YtDlpRuntimeStatus,
    val activeVersion: String,
    val bundledVersion: String,
    val pendingVersion: String?,
    val lastCheckedAtMillis: Long?,
    val lastUpdatedAtMillis: Long?,
    val nextUpdateCountdown: String?,
    val intervalProgress: Float,
    val remainingManualUpdates: Int,
    val rateLimitCountdown: String?,
    val isChecking: Boolean,
)

data class YtDlpSettingsSnackbarEvent(
    @StringRes val messageRes: Int,
)

@HiltViewModel
class YtDlpSettingsViewModel
    @Inject
    constructor(
        observeYtDlpSettings: ObserveYtDlpSettingsUseCase,
        private val checkForYtDlpUpdate: CheckForYtDlpUpdateUseCase,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow<YtDlpSettingsUiState>(YtDlpSettingsUiState.Loading)
        val state: StateFlow<YtDlpSettingsUiState> = mutableState.asStateFlow()

        private val mutableEvents = MutableSharedFlow<YtDlpSettingsSnackbarEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<YtDlpSettingsSnackbarEvent> = mutableEvents.asSharedFlow()

        private var latestDomainState: YtDlpSettingsDomainState? = null
        private var updateJob: Job? = null

        init {
            viewModelScope.launch {
                observeYtDlpSettings().collect { domain ->
                    latestDomainState = domain
                    mutableState.value = domain.toUiState()
                }
            }
        }

        fun checkForUpdates() {
            if (updateJob?.isActive == true || latestDomainState == null) return
            updateJob =
                viewModelScope.launch {
                    try {
                        when (val result = checkForYtDlpUpdate()) {
                            is ManualYtDlpUpdateResult.Updated -> {
                                mutableEvents.emit(YtDlpSettingsSnackbarEvent(R.string.ytdlp_update_downloaded))
                            }

                            ManualYtDlpUpdateResult.UpToDate -> {
                                mutableEvents.emit(YtDlpSettingsSnackbarEvent(R.string.ytdlp_up_to_date))
                            }

                            is ManualYtDlpUpdateResult.RateLimited -> {
                                mutableEvents.emit(YtDlpSettingsSnackbarEvent(R.string.ytdlp_rate_limited))
                            }

                            is ManualYtDlpUpdateResult.Failed -> {
                                reportException(result.cause)
                                mutableEvents.emit(YtDlpSettingsSnackbarEvent(R.string.ytdlp_update_failed))
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } finally {
                        updateJob = null
                    }
                }
        }

        private fun YtDlpSettingsDomainState.toUiState(): YtDlpSettingsUiState {
            if (runtime.activeVersion.isBlank()) {
                return if (runtime.lastFailure == null) {
                    YtDlpSettingsUiState.Empty
                } else {
                    YtDlpSettingsUiState.Error(R.string.ytdlp_runtime_load_failed)
                }
            }
            val remaining =
                runtime.lastCheckedAtMillis
                    ?.plusSafely(YT_DLP_UPDATE_INTERVAL_MILLIS)
                    ?.minus(nowMillis)
                    ?.coerceAtLeast(0L)
            val elapsed =
                remaining?.let {
                    (YT_DLP_UPDATE_INTERVAL_MILLIS - it).coerceIn(0L, YT_DLP_UPDATE_INTERVAL_MILLIS)
                } ?: 0L
            val rateLimitRemaining =
                rateLimitResetsAtMillis
                    ?.minus(nowMillis)
                    ?.coerceAtLeast(0L)
            return YtDlpSettingsUiState.Success(
                YtDlpSettingsUiData(
                    status = runtime.status,
                    activeVersion = runtime.activeVersion,
                    bundledVersion = runtime.bundledVersion,
                    pendingVersion = runtime.pendingVersion,
                    lastCheckedAtMillis = runtime.lastCheckedAtMillis,
                    lastUpdatedAtMillis = runtime.lastUpdatedAtMillis,
                    nextUpdateCountdown = remaining?.toCountdown(),
                    intervalProgress = elapsed.toFloat() / YT_DLP_UPDATE_INTERVAL_MILLIS.toFloat(),
                    remainingManualUpdates = remainingUpdates,
                    rateLimitCountdown = rateLimitRemaining?.toCountdown(),
                    isChecking = runtime.status == YtDlpRuntimeStatus.CHECKING,
                ),
            )
        }

        private fun Long.plusSafely(value: Long): Long =
            if (this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value

        private fun Long.toCountdown(): String {
            val totalSeconds = this / 1_000L
            val hours = totalSeconds / 3_600L
            val minutes = (totalSeconds % 3_600L) / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
