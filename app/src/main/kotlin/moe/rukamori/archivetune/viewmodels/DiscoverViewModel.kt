/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.repository.WeeklyDiscoverRepository
import moe.rukamori.archivetune.ui.component.DiscoverCardState
import timber.log.Timber
import javax.inject.Inject

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState

    data class Success(
        val cardState: DiscoverCardState,
        val savedCount: Int,
        val weeklyPlaylistTitle: String,
    ) : DiscoverUiState

    data object Empty : DiscoverUiState
    data class Error(val message: String) : DiscoverUiState
}

@HiltViewModel
class DiscoverViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: WeeklyDiscoverRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
        val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

        private val candidateSongs = mutableListOf<SongItem>()
        private var currentIndex = 0
        private var savedCount = 0
        private var isPlayingPreview = false
        private var autoPlayJob: Job? = null
        private var playerListener: Player.Listener? = null
        private var previousRepeatMode: Int? = null

        init {
            loadCards()
        }

        private fun calculateChorusSeekMs(duration: Long): Long {
            if (duration <= 0L) return 0L
            return if (duration > 45_000L) {
                (duration * 0.30).toLong()
            } else if (duration > 10_000L) {
                (duration * 0.20).toLong()
            } else {
                0L
            }
        }

        private fun ensurePlayerListener(playerConnection: PlayerConnection) {
            val player = playerConnection.player
            if (previousRepeatMode == null) {
                previousRepeatMode = player.repeatMode
            }
            player.repeatMode = Player.REPEAT_MODE_ONE

            if (playerListener == null) {
                val listener =
                    object : Player.Listener {
                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int,
                        ) {
                            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
                                oldPosition.mediaItemIndex == newPosition.mediaItemIndex
                            ) {
                                val duration = player.duration
                                val chorusSeekMs = calculateChorusSeekMs(duration)
                                player.seekTo(chorusSeekMs)
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                                val duration = player.duration
                                val chorusSeekMs = calculateChorusSeekMs(duration)
                                player.seekTo(currentIndex, chorusSeekMs)
                                player.playWhenReady = true
                            }
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                val duration = player.duration
                                val chorusSeekMs = calculateChorusSeekMs(duration)
                                player.seekTo(currentIndex, chorusSeekMs)
                                player.playWhenReady = true
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlayingPreview != isPlaying) {
                                isPlayingPreview = isPlaying
                                updateSuccessState()
                            }
                        }
                    }
                playerListener = listener
                player.addListener(listener)
            }
        }

        fun loadCards() {
            viewModelScope.launch {
                _uiState.value = DiscoverUiState.Loading
                runCatching {
                    val cards = repository.fetchDiscoverCards(limit = 30)
                    candidateSongs.clear()
                    candidateSongs.addAll(cards)
                    currentIndex = 0
                    savedCount = 0

                    if (candidateSongs.isEmpty()) {
                        _uiState.value = DiscoverUiState.Empty
                    } else {
                        updateSuccessState()
                    }
                }.onFailure { throwable ->
                    Timber.e(throwable, "Failed to load discover cards")
                    val errorDetails = throwable.localizedMessage?.takeIf { it.isNotBlank() } ?: throwable.message
                    _uiState.value = DiscoverUiState.Error(
                        if (!errorDetails.isNullOrBlank()) {
                            context.getString(R.string.discover_error_prefix, errorDetails)
                        } else {
                            context.getString(R.string.discover_error_generic)
                        },
                    )
                }
            }
        }

        fun autoPlayCurrentCard(playerConnection: PlayerConnection) {
            val current = candidateSongs.getOrNull(currentIndex) ?: return
            autoPlayJob?.cancel()
            autoPlayJob = viewModelScope.launch {
                runCatching {
                    runCatching {
                        val filesDir = playerConnection.service.filesDir
                        filesDir.resolve("persistent_queue.data").delete()
                        filesDir.resolve("persistent_player_state.data").delete()
                    }
                    playerConnection.isDiscoverPlayback.value = true
                    ensurePlayerListener(playerConnection)
                    val player = playerConnection.player
                    player.playWhenReady = false

                    if (player.mediaItemCount == 0 || player.getMediaItemAt(0).mediaId != candidateSongs.firstOrNull()?.id) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = repository.getCurrentWeekPlaylistName(),
                                items = candidateSongs.map { it.toMediaItem() },
                                startIndex = currentIndex,
                            )
                        )
                    } else {
                        if (player.mediaItemCount < candidateSongs.size) {
                            val newItems = candidateSongs.drop(player.mediaItemCount).map { it.toMediaItem() }
                            player.addMediaItems(newItems)
                        }
                        if (player.currentMediaItemIndex != currentIndex) {
                            player.seekToDefaultPosition(currentIndex)
                        }
                    }

                    prefetchNextSongStreams()

                    var elapsedMs = 0
                    while (elapsedMs < 4000) {
                        if (player.playbackState == Player.STATE_READY) {
                            val duration = player.duration
                            val chorusSeekMs = calculateChorusSeekMs(duration)
                            player.seekTo(chorusSeekMs)
                            delay(50.milliseconds)
                            player.playWhenReady = true
                            break
                        }
                        delay(100.milliseconds)
                        elapsedMs += 100
                    }
                    if (!player.playWhenReady) {
                        val duration = player.duration
                        val chorusSeekMs = calculateChorusSeekMs(duration)
                        if (chorusSeekMs > 0) {
                            player.seekTo(chorusSeekMs)
                        }
                        player.playWhenReady = true
                    }
                    isPlayingPreview = true
                    updateSuccessState()
                }.onFailure { e -> Timber.e(e, "Failed to autoplay discover preview") }
            }
        }

        fun stopPreview(playerConnection: PlayerConnection) {
            autoPlayJob?.cancel()
            try {
                runCatching {
                    val player = playerConnection.player
                    playerListener?.let {
                        player.removeListener(it)
                        playerListener = null
                    }
                    previousRepeatMode?.let { mode ->
                        player.repeatMode = mode
                        previousRepeatMode = null
                    }
                    playerConnection.service.stopAndClearPlayback(clearPersistentState = true)
                }
            } finally {
                playerConnection.isDiscoverPlayback.value = false
                isPlayingPreview = false
                updateSuccessState()
            }
        }

        private fun prefetchNextSongStreams() {
            viewModelScope.launch(Dispatchers.IO) {
                val nextSongs = candidateSongs.drop(currentIndex + 1).take(5)
                for (song in nextSongs) {
                    runCatching {
                        YouTube.next(WatchEndpoint(videoId = song.id))
                    }
                }
            }
        }

        fun swipeLeft(playerConnection: PlayerConnection) {
            advanceCard(playerConnection)
        }

        fun swipeRight(song: SongItem, playerConnection: PlayerConnection) {
            viewModelScope.launch {
                runCatching {
                    repository.saveCardToWeeklyPlaylist(song, toggleLiked = false)
                    savedCount++
                    advanceCard(playerConnection)
                }.onFailure { e -> Timber.e(e, "Failed to save card to weekly playlist") }
            }
        }

        fun togglePreview(playerConnection: PlayerConnection) {
            val player = playerConnection.player
            if (isPlayingPreview || player.isPlaying) {
                player.pause()
                isPlayingPreview = false
                updateSuccessState()
            } else {
                if (player.mediaItemCount > 0 && player.playbackState != Player.STATE_IDLE) {
                    if (player.playbackState == Player.STATE_ENDED) {
                        val duration = player.duration
                        val chorusSeekMs = calculateChorusSeekMs(duration)
                        player.seekTo(chorusSeekMs)
                    }
                    player.playWhenReady = true
                    player.play()
                    isPlayingPreview = true
                    updateSuccessState()
                } else {
                    autoPlayCurrentCard(playerConnection)
                }
            }
        }

        private fun advanceCard(playerConnection: PlayerConnection) {
            currentIndex++
            if (currentIndex >= candidateSongs.size - 3) {
                prefetchMoreCards()
            }
            if (currentIndex < candidateSongs.size) {
                autoPlayCurrentCard(playerConnection)
            } else {
                stopPreview(playerConnection)
            }
            updateSuccessState()
        }

        private fun prefetchMoreCards() {
            viewModelScope.launch {
                runCatching {
                    val newCards = repository.fetchDiscoverCards(limit = 20)
                    val existingIds = candidateSongs.map { it.id }.toSet()
                    val uniqueNew = newCards.filterNot { it.id in existingIds }
                    candidateSongs.addAll(uniqueNew)
                    updateSuccessState()
                }.onFailure { e -> Timber.w(e, "Failed to prefetch additional cards") }
            }
        }

        private fun updateSuccessState() {
            _uiState.value = DiscoverUiState.Success(
                cardState = DiscoverCardState(
                    songs = candidateSongs.toList(),
                    currentIndex = currentIndex,
                    isPlayingPreview = isPlayingPreview,
                ),
                savedCount = savedCount,
                weeklyPlaylistTitle = repository.getCurrentWeekPlaylistName(),
            )
        }

        override fun onCleared() {
            super.onCleared()
            autoPlayJob?.cancel()
        }
    }
