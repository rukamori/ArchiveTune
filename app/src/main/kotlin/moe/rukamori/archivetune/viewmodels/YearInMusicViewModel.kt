/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ListeningTotals
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.utils.reportException
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class YearInMusicViewModel
    @Inject
    constructor(
        val database: MusicDatabase,
    ) : ViewModel() {
        private val selectedYear = MutableStateFlow(LocalDateTime.now().year)

        private val firstEvent =
            database
                .firstEvent()
                .stateIn(viewModelScope, SharingStarted.Lazily, null)

        private val availableYears =
            firstEvent
                .map { event ->
                    val startYear = event?.event?.timestamp?.year ?: LocalDateTime.now().year
                    val currentYear = LocalDateTime.now().year
                    (currentYear downTo startYear).toList()
                }.stateIn(viewModelScope, SharingStarted.Lazily, listOf(LocalDateTime.now().year))

        private fun getYearStartTimestamp(year: Int): Long =
            LocalDateTime
                .of(year, 1, 1, 0, 0, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()

        private fun getYearEndTimestamp(year: Int): Long =
            LocalDateTime
                .of(year, 12, 31, 23, 59, 59)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()

        private val topSongsStats =
            selectedYear
                .flatMapLatest { year ->
                    database.mostPlayedSongsStats(
                        fromTimeStamp = getYearStartTimestamp(year),
                        limit = 5,
                        toTimeStamp = getYearEndTimestamp(year),
                    )
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val topSongs =
            selectedYear
                .flatMapLatest { year ->
                    database
                        .mostPlayedSongs(
                            fromTimeStamp = getYearStartTimestamp(year),
                            limit = 5,
                            toTimeStamp = getYearEndTimestamp(year),
                        ).map { songs -> songs.filter { song -> song.artists.none { it.blockedAt != null } } }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val topArtists =
            selectedYear
                .flatMapLatest { year ->
                    database
                        .mostPlayedArtists(
                            fromTimeStamp = getYearStartTimestamp(year),
                            limit = 5,
                            toTimeStamp = getYearEndTimestamp(year),
                        ).map { artists ->
                            artists.filter { it.artist.blockedAt == null && it.artist.isYouTubeArtist }
                        }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val topAlbums =
            selectedYear
                .flatMapLatest { year ->
                    database
                        .mostPlayedAlbums(
                            fromTimeStamp = getYearStartTimestamp(year),
                            limit = 5,
                            toTimeStamp = getYearEndTimestamp(year),
                        ).map { albums -> albums.filter { album -> album.artists.none { it.blockedAt != null } } }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        private val listeningTotals =
            selectedYear
                .flatMapLatest { year ->
                    database.listeningTotals(
                        fromTimestamp = getYearStartTimestamp(year),
                        toTimestamp = getYearEndTimestamp(year),
                    )
                }.stateIn(viewModelScope, SharingStarted.Lazily, ListeningTotals(0, 0L))

        private val recapData =
            combine(
                topSongsStats,
                topSongs,
                topArtists,
                topAlbums,
                listeningTotals,
            ) { songStats, songs, artists, albums, totals ->
                YearInMusicRecapData(
                    topSongsStats = songStats,
                    topSongs = songs,
                    topArtists = artists,
                    topAlbums = albums,
                    totalListeningTime = totals.totalTimeListened,
                    totalSongsPlayed = totals.totalPlayCount.toLong(),
                )
            }

        val uiState: StateFlow<YearInMusicUiState> =
            combine(
                selectedYear,
                availableYears,
                recapData,
            ) { year, years, data ->
                YearInMusicUiState.Content(
                    selectedYear = year,
                    availableYears = years,
                    topSongsStats = data.topSongsStats,
                    topSongs = data.topSongs,
                    topArtists = data.topArtists,
                    topAlbums = data.topAlbums,
                    totalListeningTime = data.totalListeningTime,
                    totalSongsPlayed = data.totalSongsPlayed,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    YearInMusicUiState.Content(
                        selectedYear = LocalDateTime.now().year,
                        availableYears = listOf(LocalDateTime.now().year),
                    ),
            )

        fun selectYear(year: Int) {
            selectedYear.value = year
        }

        init {
            viewModelScope.launch {
                topArtists.collect { artists ->
                    artists
                        .map { it.artist }
                        .filter {
                            it.thumbnailUrl == null || Duration.between(
                                it.lastUpdateTime,
                                LocalDateTime.now(),
                            ) > Duration.ofDays(10)
                        }.forEach { artist ->
                            YouTube.artist(artist.id).onSuccess { artistPage ->
                                database.query {
                                    update(artist, artistPage)
                                }
                            }
                        }
                }
            }

            viewModelScope.launch {
                topAlbums.collect { albums ->
                    albums
                        .filter { it.album.songCount == 0 }
                        .forEach { album ->
                            YouTube
                                .album(album.id)
                                .onSuccess { albumPage ->
                                    database.query {
                                        update(album.album, albumPage, album.artists)
                                    }
                                }.onFailure {
                                    reportException(it)
                                    if (it.message?.contains("NOT_FOUND") == true) {
                                        database.query {
                                            delete(album.album)
                                        }
                                    }
                                }
                        }
                }
            }
        }
    }

@Immutable
private data class YearInMusicRecapData(
    val totalListeningTime: Long,
    val totalSongsPlayed: Long,
    val topSongsStats: List<SongWithStats>,
    val topSongs: List<Song>,
    val topArtists: List<Artist>,
    val topAlbums: List<Album>,
)

sealed interface YearInMusicUiState {
    val selectedYear: Int
    val availableYears: List<Int>

    @Immutable
    data class Content(
        override val selectedYear: Int,
        override val availableYears: List<Int>,
        val totalListeningTime: Long = 0L,
        val totalSongsPlayed: Long = 0L,
        val topSongsStats: List<SongWithStats> = emptyList(),
        val topSongs: List<Song> = emptyList(),
        val topArtists: List<Artist> = emptyList(),
        val topAlbums: List<Album> = emptyList(),
    ) : YearInMusicUiState {
        val hasData: Boolean
            get() = topSongsStats.isNotEmpty() || topArtists.isNotEmpty() || topAlbums.isNotEmpty()
    }
}
