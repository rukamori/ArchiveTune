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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.HistorySource
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.pages.HistoryPage
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        val database: MusicDatabase,
    ) : ViewModel() {
        var historySource = MutableStateFlow(HistorySource.LOCAL)
        private val _remoteHistoryState = MutableStateFlow<RemoteHistoryUiState>(RemoteHistoryUiState.Loading)
        val remoteHistoryState: StateFlow<RemoteHistoryUiState> = _remoteHistoryState

        private val today = LocalDate.now()
        private val thisMonday = today.with(DayOfWeek.MONDAY)
        private val lastMonday = thisMonday.minusDays(7)

        val events =
            database
                .events()
                .map { events ->
                    events
                        .groupBy {
                            val date = it.event.timestamp.toLocalDate()
                            val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
                            when {
                                daysAgo == 0 -> DateAgo.Today
                                daysAgo == 1 -> DateAgo.Yesterday
                                date >= thisMonday -> DateAgo.ThisWeek
                                date >= lastMonday -> DateAgo.LastWeek
                                else -> DateAgo.Other(date.withDayOfMonth(1))
                            }
                        }.toSortedMap(
                            compareBy { dateAgo ->
                                when (dateAgo) {
                                    DateAgo.Today -> 0L
                                    DateAgo.Yesterday -> 1L
                                    DateAgo.ThisWeek -> 2L
                                    DateAgo.LastWeek -> 3L
                                    is DateAgo.Other -> ChronoUnit.DAYS.between(dateAgo.date, today)
                                }
                            },
                        ).mapValues { entry ->
                            entry.value.distinctBy { it.song.id }
                        }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

        init {
            viewModelScope.launch {
                fetchRemoteHistorySilent()
            }
        }

        fun fetchRemoteHistory() {
            _remoteHistoryState.value = RemoteHistoryUiState.Loading
            viewModelScope.launch(Dispatchers.IO) {
                YouTube
                    .musicHistory()
                    .onSuccess {
                        _remoteHistoryState.value = it.toRemoteUiState()
                    }.onFailure {
                        _remoteHistoryState.value = RemoteHistoryUiState.Error
                        reportException(it)
                    }
            }
        }

        /**
         * Fetches remote history without transitioning the UI to a Loading state.
         *
         * - [RemoteHistoryUiState.Error]   → delegates to [fetchRemoteHistory] (user sees spinner)
         * - [RemoteHistoryUiState.Loading] → fetches silently; transitions to Error on failure
         * - [RemoteHistoryUiState.Empty]   → fetches silently; transitions to Error on failure
         * - [RemoteHistoryUiState.Success] → fetches silently; keeps cached data + logs warning on failure
         *
         * Call from a coroutine context (e.g. LaunchedEffect or viewModelScope.launch).
         */
        suspend fun fetchRemoteHistorySilent() {
            val snapshot = _remoteHistoryState.value

            if (snapshot is RemoteHistoryUiState.Error) {
                fetchRemoteHistory()
                return
            }

            withContext(Dispatchers.IO) {
                try {
                    val page = YouTube.musicHistory().getOrThrow()
                    _remoteHistoryState.value = page.toRemoteUiState()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("History").w(e, "Silent remote history fetch failed")
                    when (snapshot) {
                        is RemoteHistoryUiState.Success -> {
                            // Keep cached data; don't disrupt the user
                        }

                        is RemoteHistoryUiState.Loading,
                        is RemoteHistoryUiState.Empty,
                        -> {
                            _remoteHistoryState.value = RemoteHistoryUiState.Error
                        }
                    }
                }
            }
        }

        private fun HistoryPage.toRemoteUiState(): RemoteHistoryUiState =
            if (sections?.any { it.songs.isNotEmpty() } == true) {
                RemoteHistoryUiState.Success(this)
            } else {
                RemoteHistoryUiState.Empty
            }

        /**
         * Non-suspend wrapper for call sites that are not already in a coroutine
         * (e.g. click handlers in Compose).
         */
        fun enqueueSilentFetch() {
            viewModelScope.launch(Dispatchers.IO) {
                fetchRemoteHistorySilent()
            }
        }

        fun removeEventsFromHistory(eventIds: List<Long>) {
            val uniqueEventIds = eventIds.distinct()
            if (uniqueEventIds.isEmpty()) return

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    database.deleteEventsByIds(uniqueEventIds)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Throwable) {
                    reportException(exception)
                }
            }
        }
    }

sealed interface RemoteHistoryUiState {
    data object Loading : RemoteHistoryUiState

    data class Success(
        val page: HistoryPage,
    ) : RemoteHistoryUiState

    data object Empty : RemoteHistoryUiState

    data object Error : RemoteHistoryUiState
}

sealed class DateAgo {
    data object Today : DateAgo()

    data object Yesterday : DateAgo()

    data object ThisWeek : DateAgo()

    data object LastWeek : DateAgo()

    class Other(
        val date: LocalDate,
    ) : DateAgo() {
        override fun equals(other: Any?): Boolean {
            if (other is Other) return date == other.date
            return super.equals(other)
        }

        override fun hashCode(): Int = date.hashCode()
    }
}
