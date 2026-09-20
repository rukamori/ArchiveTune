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
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.browse.BrowseAction
import moe.rukamori.archivetune.browse.BrowseEvent
import moe.rukamori.archivetune.browse.BrowseScreenState
import moe.rukamori.archivetune.browse.LoadBrowseContinuationUseCase
import moe.rukamori.archivetune.browse.LoadBrowseUseCase
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val loadBrowse: LoadBrowseUseCase,
        private val loadBrowseContinuation: LoadBrowseContinuationUseCase,
    ) : ViewModel() {
        private val browseId = Uri.decode(savedStateHandle.get<String>("browseId").orEmpty()).trim()
        private val _screenState = MutableStateFlow<BrowseScreenState>(BrowseScreenState.Loading)
        val screenState = _screenState.asStateFlow()
        private val eventChannel = Channel<BrowseEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()
        private var continuation: String? = null
        private var loadJob: Job? = null
        private var paginationJob: Job? = null

        init {
            load()
        }

        fun onAction(action: BrowseAction) {
            when (action) {
                BrowseAction.Retry -> load()
                BrowseAction.LoadMore -> loadMore()
                is BrowseAction.OpenItem -> openItem(action.itemId)
                is BrowseAction.OpenItemMenu -> openItemMenu(action.itemId)
            }
        }

        private fun load() {
            paginationJob?.cancel()
            loadJob?.cancel()
            _screenState.value = BrowseScreenState.Loading
            loadJob =
                viewModelScope.launch {
                    loadBrowse(browseId)
                        .onSuccess { result ->
                            continuation = result.continuation
                            _screenState.value =
                                if (result.uiState.items.isEmpty()) {
                                    BrowseScreenState.Empty(
                                        title = result.uiState.title,
                                        fallbackTitleResId = result.uiState.fallbackTitleResId,
                                    )
                                } else {
                                    BrowseScreenState.Success(result.uiState)
                                }
                        }.onFailure { throwable ->
                            continuation = null
                            reportException(throwable)
                            _screenState.value = BrowseScreenState.Error(R.string.error_unknown)
                        }
                }
        }

        private fun loadMore() {
            if (paginationJob?.isActive == true) return
            val current = _screenState.value as? BrowseScreenState.Success ?: return
            val nextContinuation = continuation?.takeIf(String::isNotBlank) ?: return
            _screenState.value = current.copy(uiState = current.uiState.copy(isLoadingMore = true))
            paginationJob =
                viewModelScope.launch {
                    try {
                        loadBrowseContinuation(nextContinuation)
                            .onSuccess { result ->
                                val latest = _screenState.value as? BrowseScreenState.Success ?: return@onSuccess
                                val existingIds = latest.uiState.items.mapTo(HashSet()) { item -> item.id }
                                val appendedItems = result.items.filterNot { item -> item.id in existingIds }
                                val newContinuation = result.continuation?.takeUnless { it == nextContinuation }
                                continuation = newContinuation
                                _screenState.value =
                                    latest.copy(
                                        uiState =
                                            latest.uiState.copy(
                                                items = ImmutableList.copyOf(latest.uiState.items + appendedItems),
                                                canLoadMore = newContinuation != null,
                                            ),
                                    )
                            }.onFailure { throwable ->
                                reportException(throwable)
                                continuation = null
                                val latest = _screenState.value as? BrowseScreenState.Success
                                if (latest != null) {
                                    _screenState.value = latest.copy(uiState = latest.uiState.copy(canLoadMore = false))
                                }
                                eventChannel.send(BrowseEvent.ShowMessage(R.string.error_unknown))
                            }
                    } finally {
                        val latest = _screenState.value as? BrowseScreenState.Success
                        if (latest != null) {
                            _screenState.value = latest.copy(uiState = latest.uiState.copy(isLoadingMore = false))
                        }
                    }
                }
        }

        private fun openItem(itemId: String) {
            val item = currentItem(itemId) ?: return
            val event =
                when (item) {
                    is AlbumItem -> BrowseEvent.OpenAlbum(item.id)
                    is PlaylistItem -> BrowseEvent.OpenPlaylist(item.id)
                    is ArtistItem -> BrowseEvent.OpenArtist(item.id)
                    is PodcastItem -> BrowseEvent.OpenPodcast(item.browseId)
                    is EpisodeItem -> BrowseEvent.PlayEpisode(item)
                    else -> return
                }
            eventChannel.trySend(event)
        }

        private fun openItemMenu(itemId: String) {
            val item = currentItem(itemId) ?: return
            if (item is AlbumItem || item is PlaylistItem || item is ArtistItem) {
                eventChannel.trySend(BrowseEvent.ShowItemMenu(item))
            }
        }

        private fun currentItem(itemId: String) =
            (_screenState.value as? BrowseScreenState.Success)
                ?.uiState
                ?.items
                ?.firstOrNull { item -> item.id == itemId }
    }
