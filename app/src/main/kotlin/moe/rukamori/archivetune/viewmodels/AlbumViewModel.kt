/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface AlbumUiState {
    data object Loading : AlbumUiState

    data object Content : AlbumUiState

    data object Empty : AlbumUiState

    data class Error(
        val isNotFound: Boolean = false,
    ) : AlbumUiState
}

private sealed interface FetchState {
    data object Pending : FetchState

    data object Success : FetchState

    data class Failed(
        val isNotFound: Boolean = false,
    ) : FetchState
}

@HiltViewModel
class AlbumViewModel
    @Inject
    constructor(
        private val database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val albumId = savedStateHandle.get<String>("albumId")!!
        val playlistId = MutableStateFlow("")
        val albumWithSongs =
            database
                .albumWithSongs(albumId)
                .map { album ->
                    album?.copy(
                        songs =
                            if (album.artists.any { it.blockedAt != null }) {
                                emptyList()
                            } else {
                                album.songs.filterBlockedArtists()
                            },
                    )
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        var otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())

        private val _fetchState = MutableStateFlow<FetchState>(FetchState.Pending)

        val uiState: StateFlow<AlbumUiState> =
            combine(albumWithSongs, _fetchState) { data, fetch ->
                when {
                    data != null && data.songs.isNotEmpty() -> AlbumUiState.Content
                    fetch is FetchState.Pending -> AlbumUiState.Loading
                    fetch is FetchState.Failed && data == null -> AlbumUiState.Error(fetch.isNotFound)
                    fetch is FetchState.Success && data != null && data.songs.isEmpty() -> AlbumUiState.Empty
                    fetch is FetchState.Failed && data != null && data.songs.isNotEmpty() -> AlbumUiState.Content
                    else -> AlbumUiState.Loading
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, AlbumUiState.Loading)

        init {
            retry()
        }

        fun retry() {
            viewModelScope.launch {
                _fetchState.value = FetchState.Pending
                val album = database.album(albumId).first()
                YouTube
                    .album(albumId)
                    .onSuccess {
                        playlistId.value = it.album.playlistId
                        val blockedArtistIds = database.getBlockedArtistIds().toSet()
                        otherVersions.value =
                            it.otherVersions.filter { version ->
                                version.artists.orEmpty().none { artist -> artist.id in blockedArtistIds }
                            }
                        database.withTransaction {
                            if (album == null) {
                                insert(it)
                            } else {
                                update(album.album, it, album.artists)
                            }
                        }
                        _fetchState.value = FetchState.Success
                    }.onFailure {
                        reportException(it)
                        val isNotFound = it.message?.contains("NOT_FOUND") == true
                        if (isNotFound) {
                            database.query {
                                album?.album?.let(::delete)
                            }
                        }
                        _fetchState.value = FetchState.Failed(isNotFound = isNotFound)
                    }
            }
        }
    }
