/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalSearchViewModel
    @Inject
    constructor(
        database: MusicDatabase,
    ) : ViewModel() {
        val query = MutableStateFlow("")
        val filter = MutableStateFlow(LocalFilter.ALL)

        val result =
            combine(query, filter) { query, filter ->
                query to filter
            }.flatMapLatest { (query, filter) ->
                if (query.isEmpty()) {
                    flowOf(LocalSearchResult("", filter, emptyMap()))
                } else {
                    when (filter) {
                        LocalFilter.ALL -> {
                            combine(
                                database.searchSongs(query, PREVIEW_SIZE),
                                database.searchAlbums(query, PREVIEW_SIZE),
                                database.searchArtists(query, PREVIEW_SIZE),
                                database.searchPlaylists(query, PREVIEW_SIZE),
                            ) { songs, albums, artists, playlists ->
                                songs.filter { song -> song.artists.none { it.blockedAt != null } } +
                                    albums.filter { album -> album.artists.none { it.blockedAt != null } } +
                                    artists.filter { artist -> artist.artist.blockedAt == null } +
                                    playlists
                            }
                        }

                        LocalFilter.SONG -> {
                            database.searchSongs(query).map { songs ->
                                songs.filter { song -> song.artists.none { it.blockedAt != null } }
                            }
                        }

                        LocalFilter.ALBUM -> {
                            database.searchAlbums(query).map { albums ->
                                albums.filter { album -> album.artists.none { it.blockedAt != null } }
                            }
                        }

                        LocalFilter.ARTIST -> {
                            database.searchArtists(query).map { artists ->
                                artists.filter { artist -> artist.artist.blockedAt == null }
                            }
                        }

                        LocalFilter.PLAYLIST -> {
                            database.searchPlaylists(query)
                        }
                    }.map { list ->
                        LocalSearchResult(
                            query = query,
                            filter = filter,
                            map =
                                list.groupBy {
                                    when (it) {
                                        is Song -> LocalFilter.SONG
                                        is Album -> LocalFilter.ALBUM
                                        is Artist -> LocalFilter.ARTIST
                                        is Playlist -> LocalFilter.PLAYLIST
                                    }
                                },
                        )
                    }
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                LocalSearchResult("", filter.value, emptyMap()),
            )

        companion object {
            const val PREVIEW_SIZE = 3
        }
    }

enum class LocalFilter {
    ALL,
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
}

data class LocalSearchResult(
    val query: String,
    val filter: LocalFilter,
    val map: Map<LocalFilter, List<LocalItem>>,
)
