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
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
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
import moe.rukamori.archivetune.musicrecognition.ObserveFloatingRecognitionSettingUseCase
import moe.rukamori.archivetune.musicrecognition.ObserveRecognitionHistoryUseCase
import moe.rukamori.archivetune.musicrecognition.RecognitionHistoryEntry
import moe.rukamori.archivetune.musicrecognition.RecognitionPhase
import moe.rukamori.archivetune.musicrecognition.RecognizeMusicUseCase
import moe.rukamori.archivetune.musicrecognition.RecognizedTrack
import moe.rukamori.archivetune.musicrecognition.SetFloatingRecognitionEnabledUseCase
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

@Immutable
data class RecognitionCaptureUiState(
    val level: Float = 0f,
    val progress: Float = 0f,
)

enum class MusicRecognitionErrorUi {
    PermissionRequired,
    PermissionBlocked,
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
    val recognizedAt: String,
    val shazamUrl: String?,
    val searchQuery: String,
)

@Immutable
data class RecognitionHistoryUiModel(
    val items: ImmutableList<RecognitionHistoryItemUiModel>,
) {
    val recentItems: ImmutableList<RecognitionHistoryItemUiModel> =
        ImmutableList.copyOf(items.take(3))
}

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
    val floatingRecognitionEnabled: Boolean,
    val floatingRecognitionAvailable: Boolean,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val permissionsDenied: Boolean = false,
)

sealed interface MusicRecognitionEvent {
    data class RequestMicrophonePermission(val requestId: Long) : MusicRecognitionEvent

    data object RecognitionStarted : MusicRecognitionEvent

    data class RecognitionFinished(val matched: Boolean) : MusicRecognitionEvent

    data object OpenAppSettings : MusicRecognitionEvent

    data object StartFloatingRecognition : MusicRecognitionEvent

    data object StopFloatingRecognition : MusicRecognitionEvent

    data class Search(
        val query: String,
    ) : MusicRecognitionEvent

    data class OpenUri(
        val uri: String,
    ) : MusicRecognitionEvent

    data class NavigateToDetails(
        val track: RecognizedTrack,
        val requestId: Long,
    ) : MusicRecognitionEvent
}

@HiltViewModel
class MusicRecognitionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        observeRecognitionHistory: ObserveRecognitionHistoryUseCase,
        observeFloatingRecognitionSetting: ObserveFloatingRecognitionSettingUseCase,
        private val filterRecognitionHistory: FilterRecognitionHistoryUseCase,
        private val recognizeMusic: RecognizeMusicUseCase,
        private val setFloatingRecognitionEnabled: SetFloatingRecognitionEnabledUseCase,
    ) : ViewModel() {
        private val emptyHistory = RecognitionHistoryUiModel(ImmutableList.of())
        private val _screenState =
            MutableStateFlow<MusicRecognitionScreenState>(
                MusicRecognitionScreenState.Empty(emptyHistory),
            )
        val screenState: StateFlow<MusicRecognitionScreenState> = _screenState.asStateFlow()

        private val _captureState = MutableStateFlow(RecognitionCaptureUiState())
        val captureState: StateFlow<RecognitionCaptureUiState> = _captureState.asStateFlow()

        private val _historyVisible = MutableStateFlow(false)
        private val _historyQuery = MutableStateFlow("")
        private val historyEntries =
            observeRecognitionHistory()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        private val _settingsState =
            MutableStateFlow(
                MusicRecognitionSettingsUiState(
                    visible = false,
                    floatingRecognitionEnabled = false,
                    floatingRecognitionAvailable = false,
                ),
            )
        val settingsState: StateFlow<MusicRecognitionSettingsUiState> = _settingsState.asStateFlow()

        val historySheetState: StateFlow<RecognitionHistorySheetUiState> =
            combine(
                _historyVisible,
                _historyQuery,
                historyEntries,
            ) { visible, query, entries ->
                RecognitionHistorySheetUiState(
                    visible = visible,
                    query = query,
                    allItems = entries.toUiModel(),
                    filteredItems = filterRecognitionHistory(entries, query).toUiModel(),
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
        private var persistedFloatingRecognitionEnabled = false
        private var permissionRequestPending = false
        private var permissionRequestId = 0L
        private var dispatchedPermissionRequestId: Long? = null

        @Volatile
        private var recognitionRequestId = 0L
        private var revealedTrack: RecognizedTrack? = null

        init {
            viewModelScope.launch {
                historyEntries.collect { entries ->
                    val history = entries.toUiModel()
                    updateHistory(history)
                }
            }

            viewModelScope.launch {
                observeFloatingRecognitionSetting().collect { setting ->
                    persistedFloatingRecognitionEnabled = setting.enabled
                    _settingsState.value =
                        _settingsState.value.copy(
                            floatingRecognitionEnabled = setting.enabled,
                            floatingRecognitionAvailable = setting.available,
                        )
                }
            }

            viewModelScope.launch {
                savedStateHandle
                    .getStateFlow(MusicRecognitionAutoStartRequestKey, 0L)
                    .collect { requestId ->
                        if (requestId != 0L) {
                            savedStateHandle[MusicRecognitionAutoStartRequestKey] = 0L
                            onListenRequested()
                        }
                    }
            }
        }

        fun onListenRequested() {
            if (permissionRequestPending || recognitionJob?.isActive == true ||
                _screenState.value is MusicRecognitionScreenState.Success
            ) return
            permissionRequestPending = true
            val request = MusicRecognitionEvent.RequestMicrophonePermission(++permissionRequestId)
            if (!_events.trySend(request).isSuccess) {
                permissionRequestPending = false
            }
        }

        fun onPermissionRequestDispatched(requestId: Long): Boolean {
            if (!permissionRequestPending || requestId != permissionRequestId ||
                dispatchedPermissionRequestId == requestId
            ) return false
            dispatchedPermissionRequestId = requestId
            return true
        }

        fun onMicrophonePermissionResult(granted: Boolean, permanentlyDenied: Boolean = false) {
            if (!permissionRequestPending || dispatchedPermissionRequestId != permissionRequestId) return
            permissionRequestPending = false
            if (!granted) {
                _screenState.value =
                    MusicRecognitionScreenState.Error(
                        error = if (permanentlyDenied) {
                            MusicRecognitionErrorUi.PermissionBlocked
                        } else {
                            MusicRecognitionErrorUi.PermissionRequired
                        },
                        history = currentHistory(),
                    )
                return
            }
            startRecognition()
        }

        fun onCancelRecognition() {
            recognitionRequestId++
            permissionRequestPending = false
            dispatchedPermissionRequestId = null
            recognitionJob?.cancel()
            recognitionJob = null
            revealedTrack = null
            _captureState.value = RecognitionCaptureUiState()
            _screenState.value = MusicRecognitionScreenState.Empty(currentHistory())
        }

        fun onAppSettingsRequested() {
            _screenState.value = MusicRecognitionScreenState.Empty(currentHistory())
            _events.trySend(MusicRecognitionEvent.OpenAppSettings)
        }

        fun onResultRevealed() {
            val track = revealedTrack ?: return
            if (_screenState.value !is MusicRecognitionScreenState.Success) return
            _events.trySend(MusicRecognitionEvent.NavigateToDetails(track, recognitionRequestId))
        }

        fun onDetailsNavigationRequested(requestId: Long): Boolean {
            if (requestId != recognitionRequestId ||
                _screenState.value !is MusicRecognitionScreenState.Success
            ) return false
            revealedTrack = null
            _captureState.value = RecognitionCaptureUiState()
            _screenState.value = MusicRecognitionScreenState.Empty(currentHistory())
            return true
        }

        fun onHistoryVisibilityChanged(visible: Boolean) {
            _historyVisible.value = visible
            if (!visible) _historyQuery.value = ""
        }

        fun onSettingsVisibilityChanged(visible: Boolean) {
            _settingsState.value = _settingsState.value.copy(visible = visible)
        }

        fun onFloatingRecognitionEnabledChanged(enabled: Boolean) {
            if (!_settingsState.value.floatingRecognitionAvailable ||
                _settingsState.value.isSaving
            ) return

            _settingsState.value =
                _settingsState.value.copy(
                    floatingRecognitionEnabled = enabled,
                    isSaving = true,
                    saveFailed = false,
                    permissionsDenied = false,
                )
            if (enabled) {
                if (!_events.trySend(MusicRecognitionEvent.StartFloatingRecognition).isSuccess) {
                    onFloatingRecognitionStartResult(started = false)
                }
            } else {
                _events.trySend(MusicRecognitionEvent.StopFloatingRecognition)
                persistFloatingRecognitionEnabled(false)
            }
        }

        fun onFloatingRecognitionStartResult(started: Boolean) {
            if (!_settingsState.value.isSaving) return
            if (started) {
                persistFloatingRecognitionEnabled(true)
            } else {
                _settingsState.value =
                    _settingsState.value.copy(
                        floatingRecognitionEnabled = persistedFloatingRecognitionEnabled,
                        isSaving = false,
                        permissionsDenied = true,
                    )
            }
        }

        private fun persistFloatingRecognitionEnabled(enabled: Boolean) {
            viewModelScope.launch {
                runCatching {
                    setFloatingRecognitionEnabled(enabled)
                    persistedFloatingRecognitionEnabled = enabled
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.e(throwable, "Failed to update floating recognition setting")
                    _settingsState.value =
                        _settingsState.value.copy(
                            floatingRecognitionEnabled = persistedFloatingRecognitionEnabled,
                            saveFailed = true,
                        )
                }
                _settingsState.value = _settingsState.value.copy(isSaving = false)
            }
        }

        fun onHistoryQueryChanged(query: String) {
            _historyQuery.value = query
        }

        fun onTrackSearchRequested(query: String) {
            query.trim().takeIf(String::isNotEmpty)?.let {
                onCancelRecognition()
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
            if (recognitionJob?.isActive == true) return
            val requestId = ++recognitionRequestId
            revealedTrack = null
            _captureState.value = RecognitionCaptureUiState()
            _screenState.value = MusicRecognitionScreenState.Loading(
                phase = RecognitionPhaseUi.Listening,
                history = currentHistory(),
            )
            recognitionJob =
                viewModelScope.launch {
                    _events.send(MusicRecognitionEvent.RecognitionStarted)
                    val result =
                        recognizeMusic(
                            onPhaseChanged = { phase ->
                                ensureActive()
                                if (phase == RecognitionPhase.Processing) {
                                    _captureState.value = RecognitionCaptureUiState(level = 0f, progress = 1f)
                                }
                                _screenState.value =
                                    MusicRecognitionScreenState.Loading(
                                        phase = phase.toUiModel(),
                                        history = currentHistory(),
                                    )
                            },
                            onCaptureProgress = { capture ->
                                if (requestId == recognitionRequestId) {
                                    _captureState.value =
                                        RecognitionCaptureUiState(level = capture.level, progress = capture.fraction)
                                }
                            },
                        )

                    ensureActive()
                    result.fold(
                        onSuccess = { track ->
                            revealedTrack = track
                            _screenState.value =
                                MusicRecognitionScreenState.Success(
                                    track = track.toRecognitionUiModel(),
                                    history = currentHistory(),
                                )
                            _events.send(MusicRecognitionEvent.RecognitionFinished(matched = true))
                        },
                        onFailure = { throwable ->
                            if (throwable is CancellationException) throw throwable
                            _captureState.value = RecognitionCaptureUiState()
                            _screenState.value =
                                MusicRecognitionScreenState.Error(
                                    error = throwable.toUiError(),
                                    history = currentHistory(),
                                )
                            _events.send(MusicRecognitionEvent.RecognitionFinished(matched = false))
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

        private fun currentHistory(): RecognitionHistoryUiModel = _screenState.value.history
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

internal fun RecognizedTrack.toRecognitionUiModel(): RecognizedTrackUiModel =
    RecognizedTrackUiModel(
        title = title,
        artist = artist,
        album = album?.takeIf(String::isNotBlank),
        artworkUrl = coverArtHqUrl?.takeIf(String::isNotBlank) ?: coverArtUrl?.takeIf(String::isNotBlank),
        metadata = listOfNotNull(genre, releaseDate).filter(String::isNotBlank).joinToString(" • "),
        label = label?.takeIf(String::isNotBlank),
        lyricsPreview = lyrics.filter(String::isNotBlank).take(6).takeIf { it.isNotEmpty() }?.joinToString("\n"),
        shazamUrl = shazamUrl?.takeIf(String::isNotBlank),
        isrc = isrc?.takeIf(String::isNotBlank),
        searchQuery = searchQuery,
    )

private fun List<RecognitionHistoryEntry>.toUiModel(): RecognitionHistoryUiModel {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return RecognitionHistoryUiModel(
        items = ImmutableList.copyOf(
            sortedByDescending { it.recognizedAtEpochMillis }.map { entry ->
                RecognitionHistoryItemUiModel(
                    stableKey = entry.stableKey,
                    title = entry.title,
                    artist = entry.artist,
                    artworkUrl = entry.coverArtHqUrl?.takeIf(String::isNotBlank)
                        ?: entry.coverArtUrl?.takeIf(String::isNotBlank),
                    recognizedAt = dateFormat.format(Date(entry.recognizedAtEpochMillis)),
                    shazamUrl = entry.shazamUrl?.takeIf(String::isNotBlank),
                    searchQuery = entry.searchQuery,
                )
            },
        ),
    )
}
