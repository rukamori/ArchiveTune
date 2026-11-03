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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.updates.ObserveUpdateSettingsUseCase
import moe.rukamori.archivetune.updates.UpdateUpdateSettingsUseCase
import moe.rukamori.archivetune.utils.GitCommit
import moe.rukamori.archivetune.utils.Updater
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

@Immutable
sealed interface UpdateSettingsScreenState {
    data object Loading : UpdateSettingsScreenState

    data class Success(
        val model: UpdateSettingsUiModel,
    ) : UpdateSettingsScreenState

    data object Empty : UpdateSettingsScreenState

    data class Error(
        @param:StringRes val messageRes: Int,
        val lastKnownModel: UpdateSettingsUiModel,
    ) : UpdateSettingsScreenState
}

@Immutable
data class UpdateSettingsUiModel(
    val automaticChecksEnabled: Boolean,
    val notificationsEnabled: Boolean,
)

@Immutable
data class CommitUiModel(
    val sha: String,
    val message: String,
    val author: String,
    val url: String,
    val avatarUrl: String?,
    val committedAtMillis: Long?,
)

@Immutable
data class CommitDayGroup(
    val dayStartMillis: Long?,
    val commits: List<CommitUiModel>,
)

@Immutable
sealed interface CommitHistoryState {
    data object Loading : CommitHistoryState

    data class Content(
        val groups: List<CommitDayGroup>,
        val commitCount: Int,
    ) : CommitHistoryState

    data object Empty : CommitHistoryState

    data object Error : CommitHistoryState
}

sealed interface UpdateSettingsAction {
    data class SetAutomaticChecksEnabled(val enabled: Boolean) : UpdateSettingsAction
    data class SetNotificationsEnabled(val enabled: Boolean) : UpdateSettingsAction
    data object JoinEarlyAccess : UpdateSettingsAction
    data object EarlyAccessLinkFailed : UpdateSettingsAction
    data object DismissError : UpdateSettingsAction
    data object RetryCommitHistory : UpdateSettingsAction
}

sealed interface UpdateSettingsEvent {
    data class OpenUri(val uri: String) : UpdateSettingsEvent
}

@HiltViewModel
class UpdateSettingsViewModel
    @Inject
    constructor(
        observeSettings: ObserveUpdateSettingsUseCase,
        private val updateSettings: UpdateUpdateSettingsUseCase,
    ) : ViewModel() {
        private val error = MutableStateFlow<Int?>(null)
        private var automaticChecksJob: Job? = null
        private var notificationsJob: Job? = null
        private var commitHistoryJob: Job? = null
        private val eventChannel = Channel<UpdateSettingsEvent>(Channel.CONFLATED)
        val events = eventChannel.receiveAsFlow()
        private val mutableCommitHistory = MutableStateFlow<CommitHistoryState>(CommitHistoryState.Loading)
        val commitHistory: StateFlow<CommitHistoryState> = mutableCommitHistory.asStateFlow()

        val state: StateFlow<UpdateSettingsScreenState> =
            combine(observeSettings(), error) { settings, errorRes ->
                val model =
                    UpdateSettingsUiModel(
                        automaticChecksEnabled = settings.automaticChecksEnabled,
                        notificationsEnabled = settings.notificationsEnabled,
                    )
                errorRes?.let { UpdateSettingsScreenState.Error(it, model) }
                    ?: UpdateSettingsScreenState.Success(model)
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.e(throwable, "Failed to observe update settings")
                emit(
                    UpdateSettingsScreenState.Error(
                        messageRes = R.string.error_unknown,
                        lastKnownModel = DEFAULT_MODEL,
                    ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UpdateSettingsScreenState.Loading,
            )

        init {
            loadCommitHistory()
        }

        fun onAction(action: UpdateSettingsAction) {
            when (action) {
                is UpdateSettingsAction.SetAutomaticChecksEnabled -> setAutomaticChecksEnabled(action.enabled)
                is UpdateSettingsAction.SetNotificationsEnabled -> setNotificationsEnabled(action.enabled)
                UpdateSettingsAction.JoinEarlyAccess ->
                    eventChannel.trySend(
                        UpdateSettingsEvent.OpenUri("https://discord.gg/archivetune-1498315636714115224"),
                    )
                UpdateSettingsAction.EarlyAccessLinkFailed -> error.value = R.string.early_access_open_failed
                UpdateSettingsAction.DismissError -> error.value = null
                UpdateSettingsAction.RetryCommitHistory -> {
                    if (mutableCommitHistory.value is CommitHistoryState.Error) loadCommitHistory()
                }
            }
        }

        private fun loadCommitHistory() {
            commitHistoryJob?.cancel()
            mutableCommitHistory.value = CommitHistoryState.Loading
            commitHistoryJob =
                viewModelScope.launch {
                    mutableCommitHistory.value =
                        Updater
                            .getCommitHistory(COMMIT_HISTORY_COUNT)
                            .fold(
                                onSuccess = { commits ->
                                    if (commits.isEmpty()) {
                                        CommitHistoryState.Empty
                                    } else {
                                        CommitHistoryState.Content(
                                            groups = commits.map(GitCommit::toUiModel).groupByDay(ZoneId.systemDefault()),
                                            commitCount = commits.size,
                                        )
                                    }
                                },
                                onFailure = { throwable ->
                                    Timber.w(throwable, "Failed to load commit history")
                                    CommitHistoryState.Error
                                },
                            )
                }
        }

        private fun setAutomaticChecksEnabled(enabled: Boolean) {
            automaticChecksJob?.cancel()
            automaticChecksJob = launchUpdate {
                updateSettings.setAutomaticChecksEnabled(enabled)
            }
        }

        private fun setNotificationsEnabled(enabled: Boolean) {
            notificationsJob?.cancel()
            notificationsJob = launchUpdate {
                updateSettings.setNotificationsEnabled(enabled)
            }
        }

        private fun launchUpdate(block: suspend () -> Unit): Job =
            viewModelScope.launch {
                error.value = null
                try {
                    block()
                } catch (throwable: CancellationException) {
                    throw throwable
                } catch (throwable: Exception) {
                    Timber.e(throwable, "Failed to update automatic update settings")
                    error.update { R.string.error_unknown }
                }
            }

        private companion object {
            const val COMMIT_HISTORY_COUNT = 30

            val DEFAULT_MODEL =
                UpdateSettingsUiModel(
                    automaticChecksEnabled = true,
                    notificationsEnabled = false,
                )
        }
    }

private fun GitCommit.toUiModel(): CommitUiModel =
    CommitUiModel(
        sha = sha,
        message = message,
        author = author,
        url = url,
        avatarUrl = authorAvatarUrl,
        committedAtMillis =
            date.takeIf(String::isNotBlank)?.let { isoDate ->
                try {
                    Instant.parse(isoDate).toEpochMilli()
                } catch (_: DateTimeParseException) {
                    null
                }
            },
    )

private fun List<CommitUiModel>.groupByDay(zone: ZoneId): List<CommitDayGroup> {
    val groups = mutableListOf<CommitDayGroup>()
    forEach { commit ->
        val dayStartMillis =
            commit.committedAtMillis?.let { millis ->
                Instant
                    .ofEpochMilli(millis)
                    .atZone(zone)
                    .toLocalDate()
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            }
        val last = groups.lastOrNull()
        if (last != null && last.dayStartMillis == dayStartMillis) {
            groups[groups.lastIndex] = last.copy(commits = last.commits + commit)
        } else {
            groups += CommitDayGroup(dayStartMillis = dayStartMillis, commits = listOf(commit))
        }
    }
    return groups
}
