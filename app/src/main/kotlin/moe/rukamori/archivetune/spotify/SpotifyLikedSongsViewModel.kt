/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class SpotifyLikedSongsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val getSpotifyLikedSongs: GetSpotifyLikedSongsUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SpotifyLikedSongsScreenState>(SpotifyLikedSongsScreenState.Loading)
        val uiState: StateFlow<SpotifyLikedSongsScreenState> = _uiState.asStateFlow()

        private val eventChannel = Channel<SpotifyLikedSongsEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        private var reloadJob: Job? = null
        private var loadedSongs: ImmutableList<SpotifyLikedSong> = ImmutableList.of()

        init {
            reload()
        }

        fun reload() {
            reloadJob?.cancel()
            _uiState.value = SpotifyLikedSongsScreenState.Loading
            reloadJob =
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val songs = ImmutableList.copyOf(getSpotifyLikedSongs())
                        loadedSongs = songs
                        _uiState.value =
                            if (songs.isEmpty()) {
                                SpotifyLikedSongsScreenState.Empty
                            } else {
                                SpotifyLikedSongsScreenState.Success(
                                    songs = ImmutableList.copyOf(songs.map(SpotifyLikedSong::toUiModel)),
                                )
                            }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        reportException(error)
                        _uiState.value = SpotifyLikedSongsScreenState.Error
                    }
                }
        }

        fun playAll(shuffled: Boolean) {
            if (loadedSongs.isEmpty()) return
            val songs = if (shuffled) loadedSongs.shuffled() else loadedSongs
            requestPlayback(songs = songs, startIndex = 0)
        }

        fun playSong(index: Int) {
            if (index !in loadedSongs.indices) return
            requestPlayback(songs = loadedSongs, startIndex = index)
        }

        private fun requestPlayback(
            songs: List<SpotifyLikedSong>,
            startIndex: Int,
        ) {
            eventChannel.trySend(
                SpotifyLikedSongsEvent.PlaybackRequested(
                    queue =
                        SpotifyPlaylistQueue(
                            title = context.getString(R.string.liked_songs),
                            initialTracks = songs.map(SpotifyLikedSong::toPlaybackTrack),
                            startIndex = startIndex,
                        ),
                ),
            )
        }
    }

sealed interface SpotifyLikedSongsScreenState {
    data object Loading : SpotifyLikedSongsScreenState

    @Immutable
    data class Success(
        val songs: ImmutableList<SpotifyLikedSongUiModel>,
    ) : SpotifyLikedSongsScreenState

    data object Empty : SpotifyLikedSongsScreenState

    data object Error : SpotifyLikedSongsScreenState
}

sealed interface SpotifyLikedSongsEvent {
    data class PlaybackRequested(
        val queue: Queue,
    ) : SpotifyLikedSongsEvent
}

@Immutable
data class SpotifyLikedSongUiModel(
    val id: String,
    val title: String,
    val artistNames: ImmutableList<String>,
    val thumbnailUrl: String?,
    val durationMs: Int,
    val isExplicit: Boolean,
)

private fun SpotifyLikedSong.toUiModel(): SpotifyLikedSongUiModel =
    SpotifyLikedSongUiModel(
        id = id,
        title = title,
        artistNames = ImmutableList.copyOf(artistNames),
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
        isExplicit = isExplicit,
    )
