/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.podcast.LoadPodcastContinuationUseCase
import moe.rukamori.archivetune.podcast.LoadPodcastUseCase
import moe.rukamori.archivetune.podcast.ObservePodcastLibraryMembershipUseCase
import moe.rukamori.archivetune.podcast.PodcastAction
import moe.rukamori.archivetune.podcast.PodcastEvent
import moe.rukamori.archivetune.podcast.PodcastPlaybackRequest
import moe.rukamori.archivetune.podcast.PodcastScreenState
import moe.rukamori.archivetune.podcast.ToggleEpisodeLibraryUseCase
import moe.rukamori.archivetune.podcast.TogglePodcastSaveUseCase
import moe.rukamori.archivetune.utils.LibraryLoginRequiredException
import moe.rukamori.archivetune.utils.LibrarySyncDisabledException
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class PodcastViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val loadPodcast: LoadPodcastUseCase,
        private val loadPodcastContinuation: LoadPodcastContinuationUseCase,
        private val observeLibraryMembership: ObservePodcastLibraryMembershipUseCase,
        private val setPodcastSaved: TogglePodcastSaveUseCase,
        private val setEpisodeLibrary: ToggleEpisodeLibraryUseCase,
    ) : ViewModel() {
        private val browseId = Uri.decode(savedStateHandle.get<String>("browseId").orEmpty()).trim()
        private val _screenState = MutableStateFlow<PodcastScreenState>(PodcastScreenState.Loading)
        val screenState = _screenState.asStateFlow()
        private val eventChannel = Channel<PodcastEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()
        private var continuation: String? = null
        private var loadJob: Job? = null
        private var paginationJob: Job? = null
        private var membershipJob: Job? = null
        private var podcastSaveJob: Job? = null
        private val episodeSaveJobs = mutableMapOf<String, Job>()

        init {
            load()
        }

        fun onAction(action: PodcastAction) {
            when (action) {
                PodcastAction.Retry -> load()
                PodcastAction.LoadMore -> loadMore()
                PodcastAction.PlayAll -> playAll()
                PodcastAction.TogglePodcastSave -> togglePodcastSave()
                is PodcastAction.PlayEpisode -> playEpisode(action.episodeId)
                is PodcastAction.ToggleEpisodeLibrary -> toggleEpisodeLibrary(action.episodeId)
            }
        }

        private fun load() {
            paginationJob?.cancel()
            loadJob?.cancel()
            _screenState.value = PodcastScreenState.Loading
            loadJob =
                viewModelScope.launch {
                    loadPodcast(browseId)
                        .onSuccess { result ->
                            continuation = result.continuation
                            _screenState.value =
                                if (result.uiState.episodes.isEmpty()) {
                                    PodcastScreenState.Empty
                                } else {
                                    PodcastScreenState.Success(result.uiState)
                                }
                            observeMembership(result.uiState.episodes.map { episode -> episode.id })
                        }.onFailure { throwable ->
                            continuation = null
                            reportException(throwable)
                            _screenState.value = PodcastScreenState.Error(R.string.error_unknown)
                        }
                }
        }

        private fun loadMore() {
            if (paginationJob?.isActive == true) return
            val current = _screenState.value as? PodcastScreenState.Success ?: return
            val nextContinuation = continuation?.takeIf(String::isNotBlank) ?: return
            _screenState.value = current.copy(uiState = current.uiState.copy(isLoadingMore = true))
            paginationJob =
                viewModelScope.launch {
                    try {
                        loadPodcastContinuation(
                            continuation = nextContinuation,
                            podcastTitle = current.uiState.title,
                            podcastBrowseId = current.uiState.browseId,
                        ).onSuccess { result ->
                            val latest = _screenState.value as? PodcastScreenState.Success ?: return@onSuccess
                            val existingIds = latest.uiState.episodes.mapTo(HashSet()) { it.id }
                            val appended = result.episodes.filterNot { it.id in existingIds }
                            continuation = result.continuation
                            _screenState.value =
                                latest.copy(
                                    uiState =
                                        latest.uiState.copy(
                                            episodes = ImmutableList.copyOf(latest.uiState.episodes + appended),
                                            canLoadMore = !result.continuation.isNullOrBlank(),
                                        ),
                                )
                            observeMembership(
                                ((_screenState.value as? PodcastScreenState.Success)?.uiState?.episodes)
                                    .orEmpty()
                                    .map { episode -> episode.id },
                            )
                        }.onFailure { throwable ->
                            reportException(throwable)
                            eventChannel.send(PodcastEvent.ShowMessage(R.string.error_unknown))
                        }
                    } finally {
                        val latest = _screenState.value as? PodcastScreenState.Success
                        if (latest != null) {
                            _screenState.value = latest.copy(uiState = latest.uiState.copy(isLoadingMore = false))
                        }
                    }
                }
        }

        private fun playEpisode(episodeId: String) {
            val current = (_screenState.value as? PodcastScreenState.Success)?.uiState ?: return
            val startIndex = current.episodes.indexOfFirst { it.id == episodeId }
            if (startIndex < 0) return
            eventChannel.trySend(
                PodcastEvent.Play(
                    PodcastPlaybackRequest(
                        title = current.title,
                        items = ImmutableList.copyOf(current.episodes.map { it.playbackMetadata }),
                        startIndex = startIndex,
                    ),
                ),
            )
        }

        private fun playAll() {
            val current = (_screenState.value as? PodcastScreenState.Success)?.uiState ?: return
            if (current.episodes.isEmpty()) return
            eventChannel.trySend(
                PodcastEvent.Play(
                    PodcastPlaybackRequest(
                        title = current.title,
                        items = ImmutableList.copyOf(current.episodes.map { it.playbackMetadata }),
                        startIndex = 0,
                    ),
                ),
            )
        }

        private fun observeMembership(episodeIds: List<String>) {
            membershipJob?.cancel()
            membershipJob =
                viewModelScope.launch {
                    observeLibraryMembership(browseId, episodeIds).collect { membership ->
                        _screenState.update { state ->
                            val success = state as? PodcastScreenState.Success ?: return@update state
                            success.copy(
                                uiState =
                                    success.uiState.copy(
                                        isSaved = membership.isPodcastSaved,
                                        episodes =
                                            ImmutableList.copyOf(
                                                success.uiState.episodes.map { episode ->
                                                    episode.copy(isInLibrary = episode.id in membership.episodeIds)
                                                },
                                            ),
                                    ),
                            )
                        }
                    }
                }
        }

        private fun togglePodcastSave() {
            if (podcastSaveJob?.isActive == true) return
            val current = (_screenState.value as? PodcastScreenState.Success)?.uiState ?: return
            val save = !current.isSaved
            setPodcastSavePending(true)
            podcastSaveJob =
                viewModelScope.launch {
                    try {
                        setPodcastSaved(browseId, save).onFailure { throwable ->
                            reportException(throwable)
                            eventChannel.send(PodcastEvent.ShowMessage(throwable.messageResId()))
                        }
                    } finally {
                        setPodcastSavePending(false)
                    }
                }
        }

        private fun toggleEpisodeLibrary(episodeId: String) {
            if (episodeSaveJobs[episodeId]?.isActive == true) return
            val current = (_screenState.value as? PodcastScreenState.Success)?.uiState ?: return
            val episode = current.episodes.firstOrNull { item -> item.id == episodeId } ?: return
            val addToLibrary = !episode.isInLibrary
            setEpisodeSavePending(episodeId, true)
            episodeSaveJobs[episodeId] =
                viewModelScope.launch {
                    try {
                        setEpisodeLibrary(episode.playbackMetadata, addToLibrary).onFailure { throwable ->
                            reportException(throwable)
                            eventChannel.send(PodcastEvent.ShowMessage(R.string.error_unknown))
                        }
                    } finally {
                        setEpisodeSavePending(episodeId, false)
                        episodeSaveJobs.remove(episodeId)
                    }
                }
        }

        private fun setPodcastSavePending(isPending: Boolean) {
            _screenState.update { state ->
                val success = state as? PodcastScreenState.Success ?: return@update state
                success.copy(uiState = success.uiState.copy(isSavePending = isPending))
            }
        }

        private fun setEpisodeSavePending(
            episodeId: String,
            isPending: Boolean,
        ) {
            _screenState.update { state ->
                val success = state as? PodcastScreenState.Success ?: return@update state
                success.copy(
                    uiState =
                        success.uiState.copy(
                            episodes =
                                ImmutableList.copyOf(
                                    success.uiState.episodes.map { episode ->
                                        if (episode.id == episodeId) {
                                            episode.copy(isLibraryPending = isPending)
                                        } else {
                                            episode
                                        }
                                    },
                                ),
                        ),
                )
            }
        }

        private fun Throwable.messageResId(): Int =
            when (this) {
                is LibraryLoginRequiredException -> R.string.not_logged_in_youtube
                is LibrarySyncDisabledException -> R.string.sync_disabled
                else -> R.string.error_unknown
            }
    }
