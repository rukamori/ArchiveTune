/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.updates.AppUpdateRelease
import moe.rukamori.archivetune.updates.AppUpdateUseCase
import timber.log.Timber
import java.io.File
import javax.inject.Inject

sealed interface AppUpdateState {
    data object Loading : AppUpdateState
    data class Success(
        val release: AppUpdateRelease,
        val transfer: AppUpdateTransfer = AppUpdateTransfer.Idle,
        @param:StringRes val errorRes: Int? = null,
        val openingExternalAction: Boolean = false,
        val confirmLeave: Boolean = false,
    ) : AppUpdateState
    data object Empty : AppUpdateState
    data object Error : AppUpdateState
}

sealed interface AppUpdateTransfer {
    data object Idle : AppUpdateTransfer
    data class Downloading(
        val downloadedBytes: Long = 0,
        val totalBytes: Long = -1,
        val cancelling: Boolean = false,
    ) : AppUpdateTransfer
    data object Ready : AppUpdateTransfer
}

sealed interface AppUpdateEvent {
    data class OpenReleasePage(val url: String) : AppUpdateEvent
    data class InstallApk(val file: File) : AppUpdateEvent
    data object NavigateBack : AppUpdateEvent
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updates: AppUpdateUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Loading)
    val state = mutableState.asStateFlow()
    val updateRequired = savedStateHandle.getStateFlow<String?>("requiredInstalledVersion", null)
        .map { it == BuildConfig.VERSION_NAME }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            savedStateHandle.get<String>("requiredInstalledVersion") == BuildConfig.VERSION_NAME,
        )
    private val eventChannel = Channel<AppUpdateEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var downloadedApk: File? = null
    private var leaving = false

    init {
        checkForUpdate(savedStateHandle["forceRefresh"] ?: false)
    }

    fun checkForUpdate(forceRefresh: Boolean = true) {
        val current = mutableState.value as? AppUpdateState.Success
        if (leaving || checkJob?.isActive == true || current?.transfer is AppUpdateTransfer.Downloading || current?.openingExternalAction == true) return
        mutableState.value = AppUpdateState.Loading
        checkJob = viewModelScope.launch {
            try {
                val release = updates.check(forceRefresh)
                publishRelease(release)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to check for an app update")
                mutableState.value = AppUpdateState.Error
            }
        }
    }

    fun acceptCheckedRelease(release: AppUpdateRelease?) {
        if (savedStateHandle.get<String>("requiredInstalledVersion") == BuildConfig.VERSION_NAME && release?.isMandatory != true) return
        checkJob?.cancel()
        publishRelease(release)
    }

    private fun publishRelease(release: AppUpdateRelease?) {
        downloadedApk = null
        if (release?.isMandatory == true) savedStateHandle["requiredInstalledVersion"] = BuildConfig.VERSION_NAME
        mutableState.value = release?.let { AppUpdateState.Success(it) } ?: AppUpdateState.Empty
    }

    fun primaryAction() {
        val current = mutableState.value as? AppUpdateState.Success ?: return
        if (leaving || !current.release.isUpdateAvailable || current.openingExternalAction) return
        when {
            current.transfer is AppUpdateTransfer.Downloading -> return
            !current.release.canInstallInApp -> {
                mutableState.value = current.copy(openingExternalAction = true, errorRes = null)
                eventChannel.trySend(AppUpdateEvent.OpenReleasePage(current.release.downloadUrl))
            }
            current.transfer is AppUpdateTransfer.Ready -> requestInstall()
            else -> download(current)
        }
    }

    private fun download(current: AppUpdateState.Success) {
        if (downloadJob?.isActive == true) return
        mutableState.value = current.copy(transfer = AppUpdateTransfer.Downloading(), errorRes = null)
        downloadJob = viewModelScope.launch {
            try {
                downloadedApk = updates.download(current.release) onProgress@ { progress ->
                    val state = mutableState.value as? AppUpdateState.Success ?: return@onProgress
                    val transfer = state.transfer as? AppUpdateTransfer.Downloading ?: return@onProgress
                    if (!transfer.cancelling) {
                        mutableState.value = state.copy(
                            transfer = AppUpdateTransfer.Downloading(progress.downloadedBytes, progress.totalBytes),
                        )
                    }
                }
                val state = mutableState.value as? AppUpdateState.Success ?: return@launch
                mutableState.value = state.copy(transfer = AppUpdateTransfer.Ready, confirmLeave = false)
                requestInstall()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to download an app update")
                val state = mutableState.value as? AppUpdateState.Success ?: return@launch
                mutableState.value = state.copy(transfer = AppUpdateTransfer.Idle, errorRes = R.string.app_update_download_failed, confirmLeave = false)
            }
        }
    }

    private fun requestInstall() {
        val current = mutableState.value as? AppUpdateState.Success ?: return
        val file = downloadedApk ?: return
        mutableState.value = current.copy(openingExternalAction = true, errorRes = null)
        eventChannel.trySend(AppUpdateEvent.InstallApk(file))
    }

    fun installPermissionResult(granted: Boolean) {
        if (granted) requestInstall()
        else externalActionFinished(R.string.app_update_permission_required)
    }

    fun externalActionFinished(@StringRes errorRes: Int? = null) {
        val current = mutableState.value as? AppUpdateState.Success ?: return
        mutableState.value = current.copy(openingExternalAction = false, errorRes = errorRes)
    }

    fun back() {
        if (leaving || savedStateHandle.get<String>("requiredInstalledVersion") == BuildConfig.VERSION_NAME) return
        val current = mutableState.value as? AppUpdateState.Success
        val transfer = current?.transfer as? AppUpdateTransfer.Downloading
        if (current != null && transfer != null) {
            if (!transfer.cancelling) mutableState.value = current.copy(confirmLeave = true)
        } else {
            leaving = true
            checkJob?.cancel()
            eventChannel.trySend(AppUpdateEvent.NavigateBack)
        }
    }

    fun keepDownloading() {
        val current = mutableState.value as? AppUpdateState.Success ?: return
        mutableState.value = current.copy(confirmLeave = false)
    }

    fun cancelDownload(leave: Boolean = false) {
        if (savedStateHandle.get<String>("requiredInstalledVersion") == BuildConfig.VERSION_NAME) return
        val current = mutableState.value as? AppUpdateState.Success ?: return
        val transfer = current.transfer as? AppUpdateTransfer.Downloading ?: return
        if (transfer.cancelling) return
        if (leave) leaving = true
        mutableState.value = current.copy(transfer = transfer.copy(cancelling = true), confirmLeave = false)
        viewModelScope.launch {
            downloadJob?.cancelAndJoin()
            downloadJob = null
            downloadedApk = null
            val state = mutableState.value as? AppUpdateState.Success ?: return@launch
            mutableState.value = state.copy(transfer = AppUpdateTransfer.Idle, errorRes = null)
            if (leave) eventChannel.send(AppUpdateEvent.NavigateBack)
        }
    }
}
