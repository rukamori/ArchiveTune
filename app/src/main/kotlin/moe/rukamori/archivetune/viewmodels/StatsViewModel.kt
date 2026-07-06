/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.statToPeriod
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.EventWithSong
import moe.rukamori.archivetune.db.entities.ListeningBySlot
import moe.rukamori.archivetune.db.entities.ListeningSummary
import moe.rukamori.archivetune.db.entities.ListeningTotals
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongWithStats
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.screens.OptionStats
import moe.rukamori.archivetune.utils.reportException
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

sealed interface StatsScreenState {
    data object Loading : StatsScreenState

    data class Success(
        val data: StatsUiData,
    ) : StatsScreenState

    data object Empty : StatsScreenState

    data class Error(
        @StringRes val messageResId: Int,
    ) : StatsScreenState
}

@Immutable
data class StatsUiData(
    val selectedOption: OptionStats,
    val selectedPeriodIndex: Int,
    val mostPlayedSongs: List<Song>,
    val visibleRankedSongs: List<SongWithStats>,
    val rankedSongCount: Int,
    val mostPlayedArtists: List<Artist>,
    val mostPlayedAlbums: List<Album>,
    val listeningByHour: List<ListeningBySlot>,
    val listeningByDayOfWeek: List<ListeningBySlot>,
    val listeningSummary: ListeningSummary,
    val firstEvent: EventWithSong?,
    val isSongListExpanded: Boolean,
    val canExpandSongList: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) : ViewModel() {
        private val selectedOption = MutableStateFlow(OptionStats.CONTINUOUS)
        private val indexChips = MutableStateFlow(0)
        private val isSongListExpanded = MutableStateFlow(false)
        private val isYearPickerOpen = MutableStateFlow(false)
        private val refreshRequest = MutableStateFlow(0L)

        val yearPickerOpen: StateFlow<Boolean> = isYearPickerOpen

        fun onOptionSelected(option: OptionStats) {
            if (selectedOption.value == option) return
            selectedOption.value = option
            indexChips.value = 0
            isSongListExpanded.value = false
        }

        fun onChipIndexChanged(index: Int) {
            if (indexChips.value == index) return
            indexChips.value = index
            isSongListExpanded.value = false
        }

        fun toggleSongListExpanded() {
            isSongListExpanded.value = !isSongListExpanded.value
        }

        fun showYearPicker() {
            isYearPickerOpen.value = true
        }

        fun dismissYearPicker() {
            isYearPickerOpen.value = false
        }

        fun retry() {
            refreshRequest.value += 1L
        }

        private fun periodPair() = combine(selectedOption, indexChips) { opt, idx -> Pair(opt, idx) }

        private fun toTimestamp(
            selection: OptionStats,
            t: Int,
        ): Long =
            if (selection == OptionStats.CONTINUOUS || t == 0) {
                LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
            } else {
                statToPeriod(selection, t - 1)
            }

        private val mostPlayedSongsStats =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database.mostPlayedSongsStats(
                        fromTimeStamp = statToPeriod(selection, t),
                        limit = -1,
                        toTimeStamp = toTimestamp(selection, t),
                    )
                }

        private val mostPlayedSongs =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database
                        .mostPlayedSongs(
                            fromTimeStamp = statToPeriod(selection, t),
                            limit = -1,
                            toTimeStamp = toTimestamp(selection, t),
                        ).map { songs -> songs.filter { song -> song.artists.none { it.blockedAt != null } } }
                }

        private val mostPlayedArtists =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database
                        .mostPlayedArtists(
                            statToPeriod(selection, t),
                            limit = -1,
                            toTimeStamp = toTimestamp(selection, t),
                        ).map { artists ->
                            artists.filter { it.artist.blockedAt == null && it.artist.isYouTubeArtist }
                        }
                }

        private val mostPlayedAlbums =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database
                        .mostPlayedAlbums(
                            statToPeriod(selection, t),
                            limit = -1,
                            toTimeStamp = toTimestamp(selection, t),
                        ).map { albums -> albums.filter { album -> album.artists.none { it.blockedAt != null } } }
                }

        private val listeningByHour =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database.listeningByHour(
                        fromTimestamp = statToPeriod(selection, t),
                        toTimestamp = toTimestamp(selection, t),
                    )
                }

        private val listeningByDayOfWeek =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database.listeningByDayOfWeek(
                        fromTimestamp = statToPeriod(selection, t),
                        toTimestamp = toTimestamp(selection, t),
                    )
                }

        private val listeningTotals =
            periodPair()
                .flatMapLatest { (selection, t) ->
                    database.listeningTotals(
                        fromTimestamp = statToPeriod(selection, t),
                        toTimestamp = toTimestamp(selection, t),
                    )
                }

        private val firstEvent =
            database
                .firstEvent()

        private val primaryStats =
            combine(
                mostPlayedSongsStats,
                mostPlayedSongs,
                mostPlayedArtists,
                mostPlayedAlbums,
            ) { rankedSongs, songs, artists, albums ->
                PrimaryStats(
                    rankedSongs = rankedSongs,
                    songs = songs,
                    artists = artists,
                    albums = albums,
                )
            }

        private val listeningStats =
            combine(
                listeningByHour,
                listeningByDayOfWeek,
                listeningTotals,
                firstEvent,
            ) { byHour, byDay, totals, first ->
                ListeningStats(
                    byHour = byHour,
                    byDay = byDay,
                    totals = totals,
                    firstEvent = first,
                )
            }

        val screenState: StateFlow<StatsScreenState> =
            refreshRequest
                .flatMapLatest {
                    combine(
                        primaryStats,
                        listeningStats,
                        selectedOption,
                        indexChips,
                        isSongListExpanded,
                    ) { primary, listening, option, periodIndex, expanded ->
                        val summary =
                            ListeningSummary(
                                totalPlayCount = listening.totals.totalPlayCount,
                                totalTimeListened = listening.totals.totalTimeListened,
                                uniqueSongsCount = primary.rankedSongs.size,
                                uniqueArtistsCount = primary.artists.size,
                                uniqueAlbumsCount = primary.albums.size,
                            )
                        if (summary.totalPlayCount == 0 && primary.rankedSongs.isEmpty()) {
                            StatsScreenState.Empty
                        } else {
                            StatsScreenState.Success(
                                StatsUiData(
                                    selectedOption = option,
                                    selectedPeriodIndex = periodIndex,
                                    mostPlayedSongs = primary.songs,
                                    visibleRankedSongs =
                                        if (expanded) {
                                            primary.rankedSongs
                                        } else {
                                            primary.rankedSongs.take(COLLAPSED_SONG_COUNT)
                                        },
                                    rankedSongCount = primary.rankedSongs.size,
                                    mostPlayedArtists = primary.artists,
                                    mostPlayedAlbums = primary.albums,
                                    listeningByHour = listening.byHour,
                                    listeningByDayOfWeek = listening.byDay,
                                    listeningSummary = summary,
                                    firstEvent = listening.firstEvent,
                                    isSongListExpanded = expanded,
                                    canExpandSongList = primary.rankedSongs.size > COLLAPSED_SONG_COUNT,
                                ),
                            )
                        }
                    }.catch { throwable ->
                        if (throwable is CancellationException) throw throwable
                        reportException(throwable)
                        emit(StatsScreenState.Error(R.string.error_unknown))
                    }
                }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = StatsScreenState.Loading,
            )

        init {
            viewModelScope.launch {
                mostPlayedArtists.collect { artists ->
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
                mostPlayedAlbums.collect { albums ->
                    albums
                        .filter {
                            it.album.songCount == 0
                        }.forEach { album ->
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

        private data class PrimaryStats(
            val rankedSongs: List<SongWithStats>,
            val songs: List<Song>,
            val artists: List<Artist>,
            val albums: List<Album>,
        )

        private data class ListeningStats(
            val byHour: List<ListeningBySlot>,
            val byDay: List<ListeningBySlot>,
            val totals: ListeningTotals,
            val firstEvent: EventWithSong?,
        )

        private companion object {
            const val COLLAPSED_SONG_COUNT = 5
        }
    }
