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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.SearchHistory
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import java.io.IOException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        private val database: MusicDatabase,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val retryRequests = MutableStateFlow(0)
        private val _viewState = MutableStateFlow(SearchSuggestionViewState())
        val viewState = _viewState.asStateFlow()

        init {
            viewModelScope.launch {
                combine(query, retryRequests) { query, _ -> query }
                    .flatMapLatest { query ->
                        if (query.isBlank()) {
                            database.searchHistory().map { history ->
                                SearchSuggestionViewState(
                                    query = query,
                                    history = history,
                                )
                            }
                        } else {
                            combine(
                                database.searchHistory(query).map { it.take(MaxMatchingHistory) },
                                remoteSuggestions(query),
                            ) { history, remote ->
                                val normalizedQuery = query.trim()
                                SearchSuggestionViewState(
                                    query = query,
                                    history = history,
                                    suggestions =
                                        remote.queries.filter { suggestion ->
                                            !suggestion.equals(normalizedQuery, ignoreCase = true) &&
                                                history.none { it.query.equals(suggestion, ignoreCase = true) }
                                        },
                                    items = remote.items,
                                    remoteStatus = remote.status,
                                )
                            }
                        }
                    }.collect {
                        _viewState.value = it
                    }
            }
        }

        private fun remoteSuggestions(query: String): Flow<RemoteSuggestions> =
            flow {
                val previous = _viewState.value
                emit(
                    if (previous.query.isNotBlank()) {
                        RemoteSuggestions(
                            status = SuggestionRemoteStatus.Loading,
                            queries = previous.suggestions,
                            items = previous.items,
                        )
                    } else {
                        RemoteSuggestions(status = SuggestionRemoteStatus.Loading)
                    },
                )

                val remote =
                    YouTube.searchSuggestions(query).fold(
                        onSuccess = { result ->
                            val hideExplicit = context.dataStore.getAsync(HideExplicitKey, false)
                            val hideVideo = context.dataStore.getAsync(HideVideoKey, false)
                            RemoteSuggestions(
                                status = SuggestionRemoteStatus.Loaded,
                                queries = result.queries.distinct(),
                                items =
                                    filterAiContent(
                                        result.recommendedItems
                                            .filterExplicit(hideExplicit)
                                            .filterVideo(hideVideo),
                                        loadAiContentFilterPolicy(),
                                    ).distinctBy { it.id },
                            )
                        },
                        onFailure = { throwable ->
                            if (throwable is CancellationException) throw throwable
                            RemoteSuggestions(
                                status =
                                    if (throwable is IOException) {
                                        SuggestionRemoteStatus.Offline
                                    } else {
                                        SuggestionRemoteStatus.Failed
                                    },
                            )
                        },
                    )
                emit(remote)
            }

        fun updateQuery(query: String) {
            this.query.value = query
        }

        fun retry() {
            retryRequests.update { it + 1 }
        }

        fun deleteHistory(history: SearchHistory) {
            database.query {
                delete(history)
            }
        }

        fun clearHistory() {
            database.query {
                clearSearchHistory()
            }
        }

        private data class RemoteSuggestions(
            val status: SuggestionRemoteStatus,
            val queries: List<String> = emptyList(),
            val items: List<YTItem> = emptyList(),
        )

        private companion object {
            const val MaxMatchingHistory = 3
        }
    }

enum class SuggestionRemoteStatus {
    Idle,
    Loading,
    Loaded,
    Offline,
    Failed,
}

data class SearchSuggestionViewState(
    val query: String = "",
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val remoteStatus: SuggestionRemoteStatus = SuggestionRemoteStatus.Idle,
)
