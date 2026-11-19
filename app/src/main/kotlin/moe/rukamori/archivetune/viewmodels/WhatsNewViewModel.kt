/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.backup.BackupOperationCoordinator
import moe.rukamori.archivetune.updates.WhatsNewSection
import moe.rukamori.archivetune.updates.WhatsNewUseCase
import timber.log.Timber
import javax.inject.Inject

sealed interface WhatsNewContent {
    data object Loading : WhatsNewContent
    data class Success(val sections: ImmutableList<WhatsNewSection>) : WhatsNewContent
    data object Empty : WhatsNewContent
    data class Error(val reason: Reason) : WhatsNewContent

    enum class Reason { NOT_PUBLISHED, LOAD_FAILED }
}

sealed interface WhatsNewState {
    data object Checking : WhatsNewState
    data object Hidden : WhatsNewState
    data object StartupError : WhatsNewState
    data class Showing(
        val version: String,
        val releaseUrl: String,
        val content: WhatsNewContent = WhatsNewContent.Loading,
        val isDismissing: Boolean = false,
        val dismissalFailed: Boolean = false,
    ) : WhatsNewState
}

@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    private val whatsNew: WhatsNewUseCase,
    backupOperations: BackupOperationCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow<WhatsNewState>(WhatsNewState.Checking)
    val state = mutableState.asStateFlow()
    val backupInProgress = backupOperations.isRunning
    private val releasePageChannel = Channel<String>(Channel.BUFFERED)
    val releasePages = releasePageChannel.receiveAsFlow()
    private var loadJob: Job

    init {
        loadJob = viewModelScope.launch {
            val version = BuildConfig.VERSION_NAME
            val shouldShow = try {
                whatsNew.shouldShow(version)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to register installed version")
                mutableState.value = WhatsNewState.StartupError
                return@launch
            }
            if (!shouldShow) {
                mutableState.value = WhatsNewState.Hidden
                return@launch
            }
            mutableState.value = WhatsNewState.Showing(version, whatsNew.releasePageUrl(version))
            loadRelease(version)
        }
    }

    fun retry() {
        val current = mutableState.value as? WhatsNewState.Showing ?: return
        val content = current.content as? WhatsNewContent.Error ?: return
        if (current.isDismissing || loadJob.isActive || content.reason != WhatsNewContent.Reason.LOAD_FAILED) return
        mutableState.value = current.copy(content = WhatsNewContent.Loading)
        loadJob = viewModelScope.launch { loadRelease(current.version) }
    }

    private suspend fun loadRelease(version: String) {
        try {
            val release = whatsNew.load(version)
            val current = mutableState.value as? WhatsNewState.Showing ?: return
            mutableState.value = current.copy(
                releaseUrl = release?.url ?: whatsNew.releasePageUrl(),
                content = when {
                    release == null -> WhatsNewContent.Error(WhatsNewContent.Reason.NOT_PUBLISHED)
                    release.sections.isEmpty() -> WhatsNewContent.Empty
                    else -> WhatsNewContent.Success(release.sections)
                },
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to load installed release highlights")
            val current = mutableState.value as? WhatsNewState.Showing ?: return
            mutableState.value = current.copy(content = WhatsNewContent.Error(WhatsNewContent.Reason.LOAD_FAILED))
        }
    }

    fun dismiss(openReleaseNotes: Boolean = false) {
        val current = mutableState.value as? WhatsNewState.Showing ?: return
        if (current.isDismissing) return
        mutableState.value = current.copy(isDismissing = true, dismissalFailed = false)
        viewModelScope.launch {
            try {
                whatsNew.acknowledge(current.version)
                val releaseUrl = (mutableState.value as? WhatsNewState.Showing)?.releaseUrl ?: current.releaseUrl
                loadJob.cancel()
                mutableState.value = WhatsNewState.Hidden
                if (openReleaseNotes) releasePageChannel.send(releaseUrl)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to acknowledge installed release")
                val showing = mutableState.value as? WhatsNewState.Showing ?: return@launch
                mutableState.value = showing.copy(isDismissing = false, dismissalFailed = true)
            }
        }
    }
}
