/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.net.Uri
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.logcat.ClearLogcatUseCase
import moe.rukamori.archivetune.logcat.ExportLogcatUseCase
import moe.rukamori.archivetune.logcat.FilterLogcatUseCase
import moe.rukamori.archivetune.logcat.FormatLogcatUseCase
import moe.rukamori.archivetune.logcat.LogcatLevel
import moe.rukamori.archivetune.logcat.LogcatRecord
import moe.rukamori.archivetune.logcat.ObserveLogcatUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface LogcatScreenState {
    data object Loading : LogcatScreenState

    data class Success(
        val model: LogcatUiModel,
    ) : LogcatScreenState

    data class Empty(
        val model: LogcatUiModel,
    ) : LogcatScreenState

    data class Error(
        val messageResId: Int,
    ) : LogcatScreenState
}

@Immutable
data class LogcatUiModel(
    val entries: LogcatUiEntries,
    val query: String,
    val selectedLevels: Set<LogcatLevel>,
    val isPaused: Boolean,
    val isAutoScrollPaused: Boolean,
    val isMenuExpanded: Boolean,
    val hasLogs: Boolean,
    val isExporting: Boolean,
)

@Immutable
class LogcatUiEntries private constructor(
    private val values: List<LogcatUiEntry>,
) : List<LogcatUiEntry> by values {
    companion object {
        fun from(values: List<LogcatUiEntry>): LogcatUiEntries = LogcatUiEntries(values.toList())
    }
}

@Immutable
data class LogcatUiEntry(
    val id: String,
    val timestamp: String,
    val level: LogcatLevel,
    val tag: String?,
    val message: String,
    val isExpanded: Boolean,
)

sealed interface LogcatEffect {
    data class Copy(
        val text: String,
        val confirmationResId: Int,
    ) : LogcatEffect

    data class Share(
        val text: String,
    ) : LogcatEffect

    data class Export(
        val uri: Uri,
    ) : LogcatEffect

    data class Message(
        val messageResId: Int,
    ) : LogcatEffect
}

@HiltViewModel
class LogcatViewModel
    @Inject
    constructor(
        private val observeLogcat: ObserveLogcatUseCase,
        private val filterLogcat: FilterLogcatUseCase,
        private val clearLogcat: ClearLogcatUseCase,
        private val formatLogcat: FormatLogcatUseCase,
        private val exportLogcat: ExportLogcatUseCase,
    ) : ViewModel() {
        private val records = MutableStateFlow<List<LogcatRecord>>(emptyList())
        private val query = MutableStateFlow("")
        private val selectedLevels = MutableStateFlow(LogcatLevel.entries.toSet())
        private val paused = MutableStateFlow(false)
        private val autoScrollPaused = MutableStateFlow(false)
        private val menuExpanded = MutableStateFlow(false)
        private val exporting = MutableStateFlow(false)
        private val expandedEntryIds = MutableStateFlow<Set<String>>(emptySet())
        private val loadState = MutableStateFlow<LoadState>(LoadState.Loading)
        private val effectChannel = Channel<LogcatEffect>(capacity = Channel.BUFFERED)
        private var observationJob: Job? = null
        private var exportJob: Job? = null

        val effects = effectChannel.receiveAsFlow()

        private val content =
            combine(
                records,
                query,
                selectedLevels,
                expandedEntryIds,
            ) { currentRecords, currentQuery, currentLevels, expandedIds ->
                val filteredEntries =
                    filterLogcat(
                        entries = currentRecords,
                        query = currentQuery,
                        levels = currentLevels,
                    )
                LogcatContent(
                    entries = filteredEntries.toUiEntries(expandedIds),
                    query = currentQuery,
                    selectedLevels = currentLevels,
                    hasLogs = currentRecords.isNotEmpty(),
                )
            }

        private val controls =
            combine(
                paused,
                autoScrollPaused,
                menuExpanded,
                exporting,
            ) { isPaused, isAutoScrollPaused, isMenuExpanded, isExporting ->
                LogcatControls(
                    isPaused = isPaused,
                    isAutoScrollPaused = isAutoScrollPaused,
                    isMenuExpanded = isMenuExpanded,
                    isExporting = isExporting,
                )
            }

        val state: StateFlow<LogcatScreenState> =
            combine(
                content,
                controls,
                loadState,
            ) { currentContent, currentControls, currentLoadState ->
                when (currentLoadState) {
                    LoadState.Loading -> {
                        LogcatScreenState.Loading
                    }

                    is LoadState.Error -> {
                        LogcatScreenState.Error(currentLoadState.messageResId)
                    }

                    LoadState.Ready -> {
                        val model =
                            LogcatUiModel(
                                entries = currentContent.entries,
                                query = currentContent.query,
                                selectedLevels = currentContent.selectedLevels,
                                isPaused = currentControls.isPaused,
                                isAutoScrollPaused = currentControls.isAutoScrollPaused,
                                isMenuExpanded = currentControls.isMenuExpanded,
                                hasLogs = currentContent.hasLogs,
                                isExporting = currentControls.isExporting,
                            )
                        if (currentContent.entries.isEmpty()) {
                            LogcatScreenState.Empty(model)
                        } else {
                            LogcatScreenState.Success(model)
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LogcatScreenState.Loading,
            )

        init {
            observe()
        }

        fun retry() {
            observe()
        }

        fun updateQuery(value: String) {
            query.value = value
        }

        fun toggleLevel(level: LogcatLevel) {
            selectedLevels.update { levels ->
                if (level in levels) {
                    levels - level
                } else {
                    levels + level
                }
            }
        }

        fun togglePaused() {
            paused.update { value -> !value }
        }

        fun setMenuExpanded(expanded: Boolean) {
            menuExpanded.value = expanded
        }

        fun clear() {
            menuExpanded.value = false
            clearLogcat()
            records.value = emptyList()
            expandedEntryIds.value = emptySet()
        }

        fun share() {
            menuExpanded.value = false
            val text = formatLogcat(visibleRecords())
            if (text.isNotBlank()) {
                effectChannel.trySend(LogcatEffect.Share(text))
            }
        }

        fun export() {
            menuExpanded.value = false
            if (exportJob?.isActive == true) return

            val entries = visibleRecords()
            if (entries.isEmpty()) return

            exportJob =
                viewModelScope.launch {
                    exporting.value = true
                    try {
                        effectChannel.send(LogcatEffect.Export(exportLogcat(entries)))
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        effectChannel.send(LogcatEffect.Message(R.string.export_logs_failed))
                    } finally {
                        exporting.value = false
                    }
                }
        }

        fun copy(entryId: String) {
            val record = records.value.firstOrNull { entry -> entry.id == entryId } ?: return
            effectChannel.trySend(
                LogcatEffect.Copy(
                    text = record.message,
                    confirmationResId = R.string.copied_to_clipboard,
                ),
            )
        }

        fun toggleExpanded(entryId: String) {
            expandedEntryIds.update { ids ->
                if (entryId in ids) ids - entryId else ids + entryId
            }
        }

        fun pauseAutoScroll() {
            autoScrollPaused.value = true
        }

        fun resumeAutoScroll() {
            autoScrollPaused.value = false
        }

        private fun observe() {
            observationJob?.cancel()
            loadState.value = LoadState.Loading
            observationJob =
                viewModelScope.launch {
                    try {
                        observeLogcat().collect { entries ->
                            if (!paused.value) {
                                records.value = entries
                            }
                            loadState.value = LoadState.Ready
                        }
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        loadState.value = LoadState.Error(R.string.error_unknown)
                    }
                }
        }

        private fun visibleRecords(): List<LogcatRecord> {
            val visibleEntries =
                when (val currentState = state.value) {
                    is LogcatScreenState.Success -> currentState.model.entries

                    is LogcatScreenState.Empty,
                    is LogcatScreenState.Error,
                    LogcatScreenState.Loading,
                    -> return emptyList()
                }
            val recordsById = records.value.associateBy(LogcatRecord::id)
            return visibleEntries.mapNotNull { entry -> recordsById[entry.id] }
        }

        private fun List<LogcatRecord>.toUiEntries(expandedIds: Set<String>): LogcatUiEntries {
            val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return LogcatUiEntries.from(
                map { entry ->
                    LogcatUiEntry(
                        id = entry.id,
                        timestamp = timestampFormat.format(Date(entry.time)),
                        level = entry.level,
                        tag = entry.tag,
                        message = entry.message,
                        isExpanded = entry.id in expandedIds,
                    )
                },
            )
        }

        private sealed interface LoadState {
            data object Loading : LoadState

            data object Ready : LoadState

            data class Error(
                val messageResId: Int,
            ) : LoadState
        }

        private data class LogcatContent(
            val entries: LogcatUiEntries,
            val query: String,
            val selectedLevels: Set<LogcatLevel>,
            val hasLogs: Boolean,
        )

        private data class LogcatControls(
            val isPaused: Boolean,
            val isAutoScrollPaused: Boolean,
            val isMenuExpanded: Boolean,
            val isExporting: Boolean,
        )
    }
