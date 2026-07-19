/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class SpotifyPlaylistViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: SpotifyLibraryRepository,
        private val resolveSpotifyPlaylistDownloads: ResolveSpotifyPlaylistDownloadsUseCase,
    ) : ViewModel() {
        private val playlistId: String = savedStateHandle.get<String>("playlistId").orEmpty()

        private val _uiState = MutableStateFlow(SpotifyPlaylistUiState(isLoading = true))
        val uiState: StateFlow<SpotifyPlaylistUiState> = _uiState.asStateFlow()

        private val eventChannel = Channel<SpotifyPlaylistEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        private var reloadJob: Job? = null
        private var downloadResolutionJob: Job? = null

        init {
            reload()
        }

        fun reload() {
            if (playlistId.isBlank()) {
                _uiState.value = SpotifyPlaylistUiState(errorMessage = "Missing Spotify playlist")
                return
            }
            reloadJob?.cancel()
            downloadResolutionJob?.cancel()
            downloadResolutionJob = null
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    downloadItems = ImmutableList.of(),
                    isResolvingDownloads = false,
                )
            }
            reloadJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val playlist = repository.playlist(playlistId)
                    val tracks = repository.playlistTracks(playlistId)
                    _uiState.value =
                        SpotifyPlaylistUiState(
                            playlist = playlist,
                            tracks = tracks,
                            isLoading = false,
                        )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    reportException(error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message,
                        )
                    }
                }
            }
        }

        fun resolveDownloads() {
            val state = _uiState.value
            if (state.downloadItems.isNotEmpty()) {
                eventChannel.trySend(SpotifyPlaylistEvent.DownloadsResolved(state.downloadItems))
                return
            }
            if (state.tracks.isEmpty() || downloadResolutionJob?.isActive == true) return

            val tracks = state.tracks
            downloadResolutionJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(isResolvingDownloads = true) }
                    try {
                        val items = resolveSpotifyPlaylistDownloads(tracks)
                        if (items.isEmpty()) {
                            _uiState.update { it.copy(isResolvingDownloads = false) }
                            eventChannel.send(SpotifyPlaylistEvent.DownloadResolutionFailed)
                        } else {
                            _uiState.update {
                                it.copy(
                                    downloadItems = items,
                                    isResolvingDownloads = false,
                                )
                            }
                            eventChannel.send(SpotifyPlaylistEvent.DownloadsResolved(items))
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        reportException(error)
                        _uiState.update { it.copy(isResolvingDownloads = false) }
                        eventChannel.send(SpotifyPlaylistEvent.DownloadResolutionFailed)
                    }
                }
        }
    }

sealed interface SpotifyPlaylistEvent {
    @Immutable
    data class DownloadsResolved(
        val items: ImmutableList<SpotifyDownloadItem>,
    ) : SpotifyPlaylistEvent

    data object DownloadResolutionFailed : SpotifyPlaylistEvent
}

@Immutable
data class SpotifyPlaylistUiState(
    val playlist: SpotifyPlaylist? = null,
    val tracks: List<SpotifyTrack> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val downloadItems: ImmutableList<SpotifyDownloadItem> = ImmutableList.of(),
    val isResolvingDownloads: Boolean = false,
)
