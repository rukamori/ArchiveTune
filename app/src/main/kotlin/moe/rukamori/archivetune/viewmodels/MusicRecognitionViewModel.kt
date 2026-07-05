/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.musicrecognition.FilterRecognitionHistoryUseCase
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionAutoStartRequestKey
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionException
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionFailure
import moe.rukamori.archivetune.musicrecognition.ObserveRecognitionHistoryUseCase
import moe.rukamori.archivetune.musicrecognition.ObserveBackgroundRecognitionSettingUseCase
import moe.rukamori.archivetune.musicrecognition.RecognitionHistoryEntry
import moe.rukamori.archivetune.musicrecognition.RecognitionPhase
import moe.rukamori.archivetune.musicrecognition.RecognizeMusicUseCase
import moe.rukamori.archivetune.musicrecognition.RecognizedTrack
import moe.rukamori.archivetune.musicrecognition.SetBackgroundRecognitionEnabledUseCase
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

sealed interface MusicRecognitionScreenState {
    val history: RecognitionHistoryUiModel

    @Immutable
    data class Loading(
        val phase: RecognitionPhaseUi,
        override val history: RecognitionHistoryUiModel,
    ) : MusicRecognitionScreenState

    @Immutable
    data class Success(
        val track: RecognizedTrackUiModel,
        override val history: RecognitionHistoryUiModel,
    ) : MusicRecognitionScreenState

    @Immutable
    data class Empty(
        override val history: RecognitionHistoryUiModel,
    ) : MusicRecognitionScreenState

    @Immutable
    data class Error(
        val error: MusicRecognitionErrorUi,
        override val history: RecognitionHistoryUiModel,
    ) : MusicRecognitionScreenState
}

enum class RecognitionPhaseUi {
    Listening,
    Processing,
}

enum class MusicRecognitionErrorUi {
    PermissionRequired,
    NoMatch,
    RecordingFailed,
    SignatureFailed,
    RecognitionFailed,
}

@Immutable
data class RecognizedTrackUiModel(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val metadata: String,
    val label: String?,
    val lyricsPreview: String?,
    val shazamUrl: String?,
    val isrc: String?,
    val searchQuery: String,
)

@Immutable
data class RecognitionHistoryItemUiModel(
    val stableKey: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val metadata: String,
    val recognizedAt: String,
    val shazamUrl: String?,
    val searchQuery: String,
)

@Immutable
data class RecognitionHistoryUiModel(
    val items: List<RecognitionHistoryItemUiModel>,
)

@Immutable
data class RecognitionHistorySheetUiState(
    val visible: Boolean,
    val query: String,
    val allItems: RecognitionHistoryUiModel,
    val filteredItems: RecognitionHistoryUiModel,
)

@Immutable
data class MusicRecognitionSettingsUiState(
    val visible: Boolean,
    val backgroundRecognitionEnabled: Boolean,
    val backgroundRecognitionAvailable: Boolean,
)

sealed interface MusicRecognitionEvent {
    data object RequestMicrophonePermission : MusicRecognitionEvent

    data object RecognitionStarted : MusicRecognitionEvent

    data class Search(
        val query: String,
    ) : MusicRecognitionEvent

    data class OpenUri(
        val uri: String,
    ) : MusicRecognitionEvent
}

@HiltViewModel
class MusicRecognitionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        observeRecognitionHistory: ObserveRecognitionHistoryUseCase,
        observeBackgroundRecognitionSetting: ObserveBackgroundRecognitionSettingUseCase,
        private val filterRecognitionHistory: FilterRecognitionHistoryUseCase,
        private val recognizeMusic: RecognizeMusicUseCase,
        private val setBackgroundRecognitionEnabled: SetBackgroundRecognitionEnabledUseCase,
    ) : ViewModel() {
        private val emptyHistory = RecognitionHistoryUiModel(emptyList())
        private val _screenState =
            MutableStateFlow<MusicRecognitionScreenState>(
                MusicRecognitionScreenState.Empty(emptyHistory),
            )
        val screenState: StateFlow<MusicRecognitionScreenState> = _screenState.asStateFlow()

        private val _historyVisible = MutableStateFlow(false)
        private val _historyQuery = MutableStateFlow("")
        private val _historyUi = MutableStateFlow(emptyHistory)
        private val historyEntries =
            observeRecognitionHistory()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private val _settingsState =
            MutableStateFlow(
                MusicRecognitionSettingsUiState(
                    visible = false,
                    backgroundRecognitionEnabled = false,
                    backgroundRecognitionAvailable = false,
                ),
            )
        val settingsState: StateFlow<MusicRecognitionSettingsUiState> = _settingsState.asStateFlow()

        val historySheetState: StateFlow<RecognitionHistorySheetUiState> =
            combine(
                _historyVisible,
                _historyQuery,
                historyEntries,
                _historyUi,
            ) { visible, query, entries, allItems ->
                val filteredKeys =
                    filterRecognitionHistory(entries, query)
                        .asSequence()
                        .map { it.stableKey }
                        .toSet()
                val filteredItems =
                    RecognitionHistoryUiModel(
                        allItems.items.filter { it.stableKey in filteredKeys },
                    )
                RecognitionHistorySheetUiState(
                    visible = visible,
                    query = query,
                    allItems = allItems,
                    filteredItems = filteredItems,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    RecognitionHistorySheetUiState(
                        visible = false,
                        query = "",
                        allItems = emptyHistory,
                        filteredItems = emptyHistory,
                    ),
            )

        private val _events = Channel<MusicRecognitionEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        private var recognitionJob: Job? = null
        private var settingsUpdateJob: Job? = null
        private var persistedBackgroundRecognitionEnabled = false

        init {
            viewModelScope.launch {
                historyEntries.collect { entries ->
                    val history = entries.toUiModel()
                    _historyUi.value = history
                    updateHistory(history)
                }
            }

            viewModelScope.launch {
                observeBackgroundRecognitionSetting().collect { setting ->
                    persistedBackgroundRecognitionEnabled = setting.enabled
                    _settingsState.value =
                        _settingsState.value.copy(
                            backgroundRecognitionEnabled = setting.enabled,
                            backgroundRecognitionAvailable = setting.available,
                        )
                }
            }

            viewModelScope.launch {
                savedStateHandle
                    .getStateFlow(MusicRecognitionAutoStartRequestKey, 0L)
                    .collect { requestId ->
                        if (requestId != 0L) {
                            savedStateHandle[MusicRecognitionAutoStartRequestKey] = 0L
                            _events.send(MusicRecognitionEvent.RequestMicrophonePermission)
                        }
                    }
            }
        }

        fun onListenRequested() {
            if (recognitionJob?.isActive == true) return
            _events.trySend(MusicRecognitionEvent.RequestMicrophonePermission)
        }

        fun onMicrophonePermissionResult(granted: Boolean) {
            if (!granted) {
                _screenState.value =
                    MusicRecognitionScreenState.Error(
                        error = MusicRecognitionErrorUi.PermissionRequired,
                        history = currentHistory(),
                    )
                return
            }
            startRecognition()
        }

        fun onCancelRecognition() {
            recognitionJob?.cancel()
            recognitionJob = null
            _screenState.value = MusicRecognitionScreenState.Empty(currentHistory())
        }

        fun onHistoryVisibilityChanged(visible: Boolean) {
            _historyVisible.value = visible
            if (!visible) _historyQuery.value = ""
        }

        fun onSettingsVisibilityChanged(visible: Boolean) {
            _settingsState.value = _settingsState.value.copy(visible = visible)
        }

        fun onBackgroundRecognitionEnabledChanged(enabled: Boolean) {
            if (!_settingsState.value.backgroundRecognitionAvailable) return

            _settingsState.value =
                _settingsState.value.copy(backgroundRecognitionEnabled = enabled)
            settingsUpdateJob?.cancel()
            settingsUpdateJob =
                viewModelScope.launch {
                    runCatching {
                        setBackgroundRecognitionEnabled(enabled)
                    }.onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        Timber.e(throwable, "Failed to update background recognition setting")
                        _settingsState.value =
                            _settingsState.value.copy(
                                backgroundRecognitionEnabled =
                                    persistedBackgroundRecognitionEnabled,
                            )
                    }
                }
        }

        fun onHistoryQueryChanged(query: String) {
            _historyQuery.value = query
        }

        fun onTrackSearchRequested(query: String) {
            query.trim().takeIf(String::isNotEmpty)?.let {
                _historyVisible.value = false
                _historyQuery.value = ""
                _events.trySend(MusicRecognitionEvent.Search(it))
            }
        }

        fun onExternalUriRequested(uri: String) {
            uri.trim().takeIf(String::isNotEmpty)?.let {
                _events.trySend(MusicRecognitionEvent.OpenUri(it))
            }
        }

        private fun startRecognition() {
            recognitionJob?.cancel()
            recognitionJob =
                viewModelScope.launch {
                    _events.send(MusicRecognitionEvent.RecognitionStarted)
                    val result =
                        recognizeMusic { phase ->
                            _screenState.value =
                                MusicRecognitionScreenState.Loading(
                                    phase = phase.toUiModel(),
                                    history = currentHistory(),
                                )
                        }

                    result.fold(
                        onSuccess = { track ->
                            _screenState.value =
                                MusicRecognitionScreenState.Success(
                                    track = track.toUiModel(),
                                    history = currentHistory(),
                                )
                        },
                        onFailure = { throwable ->
                            if (throwable is CancellationException) throw throwable
                            _screenState.value =
                                MusicRecognitionScreenState.Error(
                                    error = throwable.toUiError(),
                                    history = currentHistory(),
                                )
                        },
                    )
                    recognitionJob = null
                }
        }

        private fun updateHistory(history: RecognitionHistoryUiModel) {
            _screenState.value =
                when (val state = _screenState.value) {
                    is MusicRecognitionScreenState.Empty -> state.copy(history = history)
                    is MusicRecognitionScreenState.Error -> state.copy(history = history)
                    is MusicRecognitionScreenState.Loading -> state.copy(history = history)
                    is MusicRecognitionScreenState.Success -> state.copy(history = history)
                }
        }

        private fun currentHistory(): RecognitionHistoryUiModel = _historyUi.value
    }

private fun RecognitionPhase.toUiModel(): RecognitionPhaseUi =
    when (this) {
        RecognitionPhase.Listening -> RecognitionPhaseUi.Listening
        RecognitionPhase.Processing -> RecognitionPhaseUi.Processing
    }

private fun Throwable.toUiError(): MusicRecognitionErrorUi {
    val failure = (this as? MusicRecognitionException)?.failure
    return when (failure) {
        MusicRecognitionFailure.NoMatch -> MusicRecognitionErrorUi.NoMatch
        MusicRecognitionFailure.RecordingFailed -> MusicRecognitionErrorUi.RecordingFailed
        MusicRecognitionFailure.SignatureFailed -> MusicRecognitionErrorUi.SignatureFailed
        MusicRecognitionFailure.RecognitionFailed, null -> MusicRecognitionErrorUi.RecognitionFailed
    }
}

private fun RecognizedTrack.toUiModel(): RecognizedTrackUiModel =
    RecognizedTrackUiModel(
        title = title,
        artist = artist,
        album = album,
        artworkUrl = coverArtHqUrl ?: coverArtUrl,
        metadata = listOfNotNull(genre, releaseDate).filter(String::isNotBlank).joinToString(" • "),
        label = label?.takeIf(String::isNotBlank),
        lyricsPreview = lyrics.take(6).takeIf { it.isNotEmpty() }?.joinToString("\n"),
        shazamUrl = shazamUrl?.takeIf(String::isNotBlank),
        isrc = isrc?.takeIf(String::isNotBlank),
        searchQuery = searchQuery,
    )

private fun List<RecognitionHistoryEntry>.toUiModel(): RecognitionHistoryUiModel =
    RecognitionHistoryUiModel(
        map { entry ->
            RecognitionHistoryItemUiModel(
                stableKey = entry.stableKey,
                title = entry.title,
                artist = entry.artist,
                artworkUrl = entry.coverArtHqUrl ?: entry.coverArtUrl,
                metadata =
                    listOfNotNull(entry.album, entry.genre, entry.releaseDate)
                        .filter(String::isNotBlank)
                        .joinToString(" • "),
                recognizedAt =
                    DateFormat
                        .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(entry.recognizedAtEpochMillis)),
                shazamUrl = entry.shazamUrl?.takeIf(String::isNotBlank),
                searchQuery = entry.searchQuery,
            )
        },
    )
