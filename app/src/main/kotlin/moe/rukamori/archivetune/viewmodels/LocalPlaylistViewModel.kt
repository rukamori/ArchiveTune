/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.constants.PlaylistSuggestionSource
import moe.rukamori.archivetune.constants.PlaylistSuggestionSourceKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.extensions.reversed
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.models.PlaylistSuggestion
import moe.rukamori.archivetune.models.PlaylistSuggestionPage
import moe.rukamori.archivetune.models.PlaylistSuggestionQuery
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.PlaylistSuggestionQueryBuilder
import moe.rukamori.archivetune.utils.SyncUtils
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val syncUtils: SyncUtils,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        val playlistId = savedStateHandle.get<String>("playlistId")!!
        val playlist =
            database
                .playlist(playlistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)

        val sortType: StateFlow<PlaylistSongSortType> =
            playlist
                .map { it?.playlist?.songSortType.toEnum(PlaylistSongSortType.CUSTOM) }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, PlaylistSongSortType.CUSTOM)

        val sortDescending: StateFlow<Boolean> =
            playlist
                .map { it?.playlist?.songSortDescending ?: true }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, true)

        val playlistSongs: StateFlow<List<PlaylistSong>> =
            combine(
                database.playlistSongs(playlistId),
                sortType,
                sortDescending,
            ) { songs, sortType, sortDescending ->
                val visibleSongs = songs.filter { playlistSong -> playlistSong.song.artists.none { it.blockedAt != null } }
                when (sortType) {
                    PlaylistSongSortType.CUSTOM -> {
                        visibleSongs
                    }

                    PlaylistSongSortType.CREATE_DATE -> {
                        visibleSongs.sortedBy { it.map.id }
                    }

                    PlaylistSongSortType.NAME -> {
                        visibleSongs.sortedBy { it.song.song.title }
                    }

                    PlaylistSongSortType.ARTIST -> {
                        val collator = Collator.getInstance(Locale.getDefault())
                        collator.strength = Collator.PRIMARY
                        visibleSongs
                            .sortedWith(compareBy(collator) { song -> song.song.artists.joinToString("") { artist -> artist.name } })
                            .groupBy { it.song.album?.title }
                            .flatMap { (_, songsByAlbum) ->
                                songsByAlbum.sortedBy { playlistSong ->
                                    playlistSong.song.artists.joinToString("") { artist -> artist.name }
                                }
                            }
                    }

                    PlaylistSongSortType.PLAY_TIME -> {
                        visibleSongs.sortedBy { it.song.song.totalPlayTime }
                    }
                }.reversed(sortDescending && sortType != PlaylistSongSortType.CUSTOM)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun updateSortPreference(
            newSortType: PlaylistSongSortType,
            newSortDescending: Boolean,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                database.updatePlaylistSortPreference(
                    playlistId = playlistId,
                    sortType = newSortType.name,
                    descending = newSortDescending,
                )
            }
        }

        private val _viewCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val viewCounts = _viewCounts.asStateFlow()

        private val viewCountsMutex = Mutex()
        private val viewCountsInFlight = mutableSetOf<String>()
        private val viewCountsSemaphore = Semaphore(permits = 4)

        // Playlist Suggestions State
        private val _playlistSuggestions = MutableStateFlow<PlaylistSuggestion?>(null)
        val playlistSuggestions =
            combine(_playlistSuggestions, playlistSongs) { suggestions, songs ->
                val songIds = songs.map { it.song.id }.toSet()
                val filteredItems = suggestions?.items?.filter { it.id !in songIds } ?: emptyList()
                suggestions?.copy(
                    items = filteredItems.take(10),
                )
            }.stateIn(viewModelScope, SharingStarted.Lazily, null)

        private val _isLoadingSuggestions = MutableStateFlow(false)
        val isLoadingSuggestions = _isLoadingSuggestions.asStateFlow()

        private val suggestionQueries = MutableStateFlow<List<PlaylistSuggestionQuery>>(emptyList())
        private val currentSuggestionQueryIndex = MutableStateFlow(0)
        private val suggestionsCacheTimestamp = MutableStateFlow(0L)
        private val suggestedSongIds = MutableStateFlow<Set<String>>(emptySet())

        // Mutex to prevent concurrent suggestion loading
        private val suggestionLoadMutex = Mutex()

        // Cache for current suggestion page
        private var currentSuggestionPage: PlaylistSuggestionPage? = null

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing = _isRefreshing.asStateFlow()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                val sortedSongs =
                    database
                        .playlistSongs(playlistId)
                        .first()
                        .sortedWith(compareBy({ it.map.position }, { it.map.id }))
                database.withTransaction {
                    sortedSongs.forEachIndexed { index, playlistSong ->
                        if (playlistSong.map.position != index) {
                            update(playlistSong.map.copy(position = index))
                        }
                    }
                }
            }

            viewModelScope.launch {
                playlistSongs.collect { songs ->
                    prefetchViewCounts(
                        songs
                            .asSequence()
                            .filter { !it.song.song.isLocal }
                            .map { it.song.id }
                            .toList(),
                    )
                }
            }

            // Auto-load suggestions when playlist or songs change
            viewModelScope.launch {
                combine(playlist, playlistSongs) { playlist, songs ->
                    Pair(playlist, songs)
                }.collect { (playlist, songs) ->
                    playlist?.let {
                        loadPlaylistSuggestions()
                    }
                }
            }

            // Auto-refresh suggestions when they become empty
            viewModelScope.launch {
                playlistSuggestions.collect { suggestions ->
                    if (suggestions != null && suggestions.items.isEmpty() && suggestions.hasMore && !_isLoadingSuggestions.value) {
                        loadMoreSuggestions()
                    }
                }
            }
        }

        private fun prefetchViewCounts(videoIds: List<String>) {
            val uniqueIds = videoIds.distinct().filter { it.isNotBlank() }
            if (uniqueIds.isEmpty()) return

            viewModelScope.launch(Dispatchers.IO) {
                coroutineScope {
                    uniqueIds
                        .map { videoId ->
                            async {
                                val shouldFetch =
                                    viewCountsMutex.withLock {
                                        if (_viewCounts.value.containsKey(videoId) || viewCountsInFlight.contains(videoId)) {
                                            false
                                        } else {
                                            viewCountsInFlight.add(videoId)
                                            true
                                        }
                                    }

                                if (!shouldFetch) return@async

                                try {
                                    viewCountsSemaphore.withPermit {
                                        val count = YouTube.getMediaInfo(videoId).getOrNull()?.viewCount
                                        if (count != null && count >= 0) {
                                            _viewCounts.update { current -> current + (videoId to count) }
                                        }
                                    }
                                } finally {
                                    viewCountsMutex.withLock { viewCountsInFlight.remove(videoId) }
                                }
                            }
                        }.awaitAll()
                }
            }
        }

        fun refresh() {
            if (_isRefreshing.value) return
            _isRefreshing.value = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val p = playlist.first() ?: return@launch
                    val browseId = p.playlist.browseId ?: return@launch
                    syncUtils.syncPlaylistNow(browseId = browseId, playlistId = playlistId)
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        // Playlist Suggestions Functions

        fun loadPlaylistSuggestions(forceReset: Boolean = false) {
            viewModelScope.launch {
                suggestionLoadMutex.withLock {
                    val currentSongs = playlistSongs.first()
                    val currentPlaylist = playlist.first() ?: return@withLock

                    val shouldRefresh =
                        forceReset ||
                            PlaylistSuggestionQueryBuilder.shouldRefreshSuggestions(
                                suggestionsCacheTimestamp.value,
                            )

                    if (!shouldRefresh && _playlistSuggestions.value != null) {
                        return@withLock // Use cached suggestions
                    }

                    _isLoadingSuggestions.value = true

                    // Clear state for refresh
                    _playlistSuggestions.value = null
                    currentSuggestionQueryIndex.value = 0
                    // Keep previously suggested IDs to avoid showing them again on refresh
                    // but ensure songs already in playlist are always included in the filter
                    suggestedSongIds.value = suggestedSongIds.value + currentSongs.map { it.song.id }.toSet()
                    suggestionsCacheTimestamp.value = 0L
                    currentSuggestionPage = null

                    try {
                        val suggestionSource =
                            context.dataStore.data
                                .first()[PlaylistSuggestionSourceKey]
                                .toEnum(PlaylistSuggestionSource.BOTH)

                        // Build suggestion queries
                        val queries =
                            PlaylistSuggestionQueryBuilder.buildSuggestionQueries(
                                playlistName = currentPlaylist.playlist.name,
                                playlistSongs = currentSongs,
                                suggestionSource = suggestionSource,
                            )
                        suggestionQueries.value = queries
                        suggestionsCacheTimestamp.value = System.currentTimeMillis()

                        if (queries.isEmpty()) {
                            _playlistSuggestions.value =
                                PlaylistSuggestion(
                                    items = emptyList(),
                                    continuation = null,
                                    currentQueryIndex = 0,
                                    totalQueries = 0,
                                    query = "",
                                    hasMore = false,
                                )
                            return@withLock
                        }

                        // Load first page of suggestions
                        loadNextSuggestionPage()
                    } catch (e: Exception) {
                        reportException(e)
                        if (_playlistSuggestions.value == null) {
                            _playlistSuggestions.value =
                                PlaylistSuggestion(
                                    items = emptyList(),
                                    continuation = null,
                                    currentQueryIndex = 0,
                                    totalQueries = 0,
                                    query = "",
                                    hasMore = false,
                                )
                        }
                    } finally {
                        _isLoadingSuggestions.value = false
                    }
                }
            }
        }

        fun loadMoreSuggestions() {
            viewModelScope.launch {
                suggestionLoadMutex.withLock {
                    if (_isLoadingSuggestions.value) return@withLock

                    val currentSuggestions = _playlistSuggestions.value ?: return@withLock
                    val queries = suggestionQueries.value

                    try {
                        // If we have a continuation, load more from current query
                        currentSuggestionPage?.continuation?.let { continuation ->
                            _isLoadingSuggestions.value = true
                            loadMoreFromContinuation(continuation)
                            return@withLock
                        }

                        // Otherwise, move to next query
                        val nextIndex = currentSuggestionQueryIndex.value + 1
                        if (nextIndex < queries.size) {
                            _isLoadingSuggestions.value = true
                            currentSuggestionQueryIndex.value = nextIndex
                            loadNextSuggestionPage()
                        } else {
                            // No more queries and no continuation
                            _playlistSuggestions.value = currentSuggestions.copy(hasMore = false)
                        }
                    } finally {
                        _isLoadingSuggestions.value = false
                    }
                }
            }
        }

        fun resetAndLoadPlaylistSuggestions() {
            loadPlaylistSuggestions(forceReset = true)
        }

        /**
         * Mark a suggested song as added to prevent it from appearing again
         */
        fun markSuggestionAsAdded(songId: String) {
            suggestedSongIds.value = suggestedSongIds.value + songId

            // Also remove from current suggestions list
            val currentSuggestions = _playlistSuggestions.value
            if (currentSuggestions != null) {
                _playlistSuggestions.value =
                    currentSuggestions.copy(
                        items = currentSuggestions.items.filter { it.id != songId },
                    )
            }
        }

        suspend fun addSongToPlaylist(
            song: moe.rukamori.archivetune.innertube.models.SongItem,
            browseId: String?,
        ): Boolean {
            return try {
                val playlistSetVideoId =
                    if (browseId != null) {
                        withContext(Dispatchers.IO) {
                            YouTube.addToPlaylist(browseId, song.id).getOrThrow()
                        }
                    } else {
                        null
                    }

                val added =
                    database.withTransaction {
                        // Ensure playlist exists in local database (it should, but just in case)
                        val p = getPlaylistById(playlistId)
                        if (p == null) {
                            // If not found, we can't add to it.
                            // This might happen if it's a special playlist that hasn't been created yet.
                            if (playlistId == moe.rukamori.archivetune.db.entities.PlaylistEntity.LIKED_PLAYLIST_ID) {
                                insert(
                                    moe.rukamori.archivetune.db.entities.PlaylistEntity(
                                        id = playlistId,
                                        name = context.getString(R.string.liked_songs),
                                        isEditable = false,
                                        bookmarkedAt = java.time.LocalDateTime.now(),
                                    ),
                                )
                            } else {
                                return@withTransaction false
                            }
                        }

                        // First, ensure the song and its artists are in the database
                        insert(song.toMediaMetadata())

                        // Add to local playlist
                        val maxPosition = maxPlaylistSongPosition(playlistId)
                        val position = (maxPosition ?: -1) + 1
                        insert(
                            moe.rukamori.archivetune.db.entities.PlaylistSongMap(
                                songId = song.id,
                                playlistId = playlistId,
                                position = position,
                                setVideoId = playlistSetVideoId,
                            ),
                        )
                        true
                    }

                if (!added) {
                    return false
                }

                // Update suggested song IDs to avoid duplicates
                suggestedSongIds.value = suggestedSongIds.value + song.id

                true
            } catch (e: Exception) {
                reportException(e)
                false
            }
        }

        private suspend fun loadNextSuggestionPage() {
            val queries = suggestionQueries.value
            val currentIndex = currentSuggestionQueryIndex.value

            if (currentIndex >= queries.size) return

            val currentQuery = queries[currentIndex]

            try {
                val searchFilter = YouTube.SearchFilter.FILTER_SONG

                val result =
                    YouTube
                        .search(
                            query = currentQuery.query,
                            filter = searchFilter,
                            useAccountContext = false,
                        ).getOrNull()
                        ?: return

                val filteredItems = filterSuggestionItems(result.items).shuffled().take(10)

                // If we got no new items after filtering, try to load more if available
                if (filteredItems.isEmpty() && (result.continuation != null || currentIndex < queries.size - 1)) {
                    result.continuation?.let { continuationValue ->
                        loadMoreFromContinuation(continuationValue)
                    } ?: run {
                        currentSuggestionQueryIndex.value = currentIndex + 1
                        loadNextSuggestionPage()
                    }
                    return
                }

                currentSuggestionPage =
                    PlaylistSuggestionPage(
                        items = filteredItems,
                        continuation = result.continuation,
                    )

                // Update suggested song IDs to avoid duplicates
                val newIds = filteredItems.map { item: YTItem -> item.id }.toSet()
                suggestedSongIds.value = suggestedSongIds.value + newIds

                // Update suggestions state
                val currentSuggestions = _playlistSuggestions.value
                val newSuggestions =
                    if (currentSuggestions == null) {
                        filteredItems
                    } else {
                        currentSuggestions.items + filteredItems
                    }

                _playlistSuggestions.value =
                    PlaylistSuggestion(
                        items = newSuggestions,
                        continuation = result.continuation,
                        currentQueryIndex = currentIndex,
                        totalQueries = queries.size,
                        query = currentQuery.query,
                        hasMore = result.continuation != null || currentIndex < queries.size - 1,
                    )
            } catch (e: Exception) {
                reportException(e)
            }
        }

        private suspend fun loadMoreFromContinuation(continuation: String) {
            try {
                val result =
                    YouTube
                        .searchContinuation(
                            continuation = continuation,
                            useAccountContext = false,
                        ).getOrNull()
                        ?: return

                val filteredItems = filterSuggestionItems(result.items).shuffled().take(10)

                // If we got no new items after filtering, try to move to next query if available
                if (filteredItems.isEmpty()) {
                    val currentSuggestions = _playlistSuggestions.value
                    val queries = suggestionQueries.value
                    val currentIndex = currentSuggestionQueryIndex.value

                    result.continuation?.let { continuationValue ->
                        loadMoreFromContinuation(continuationValue)
                    } ?: run {
                        if (currentIndex < queries.size - 1) {
                            currentSuggestionQueryIndex.value = currentIndex + 1
                            loadNextSuggestionPage()
                        } else {
                            // No more items and no more queries
                            if (currentSuggestions != null) {
                                _playlistSuggestions.value = currentSuggestions.copy(hasMore = false)
                            }
                        }
                    }
                    return
                }

                currentSuggestionPage =
                    PlaylistSuggestionPage(
                        items = filteredItems,
                        continuation = result.continuation,
                    )

                // Update suggested song IDs to avoid duplicates
                val moreIds = filteredItems.map { item: YTItem -> item.id }.toSet()
                suggestedSongIds.value = suggestedSongIds.value + moreIds

                // Update suggestions state
                val currentSuggestions = _playlistSuggestions.value
                if (currentSuggestions == null) {
                    _playlistSuggestions.value =
                        PlaylistSuggestion(
                            items = filteredItems,
                            continuation = result.continuation,
                            currentQueryIndex = currentSuggestionQueryIndex.value,
                            totalQueries = suggestionQueries.value.size,
                            query = suggestionQueries.value.getOrNull(currentSuggestionQueryIndex.value)?.query ?: "",
                            hasMore = result.continuation != null || currentSuggestionQueryIndex.value < suggestionQueries.value.size - 1,
                        )
                } else {
                    _playlistSuggestions.value =
                        currentSuggestions.copy(
                            items = currentSuggestions.items + filteredItems,
                            continuation = result.continuation,
                            hasMore = result.continuation != null || currentSuggestions.currentQueryIndex < suggestionQueries.value.size - 1,
                        )
                }
            } catch (e: Exception) {
                reportException(e)
            }
        }

        private suspend fun filterSuggestionItems(items: List<YTItem>): List<YTItem> {
            val hideExplicit = context.dataStore.data.first()[HideExplicitKey] ?: false
            val hideVideos = context.dataStore.data.first()[HideVideoKey] ?: false

            var filteredItems =
                items
                    .filter { item -> item.id !in suggestedSongIds.value }
                    .filterBlockedArtists(database.getBlockedArtistIds().toSet())

            if (hideExplicit) {
                filteredItems = filteredItems.filterExplicit()
            }

            if (hideVideos) {
                filteredItems = filteredItems.filterVideo()
            }
            return filteredItems
        }
    }
