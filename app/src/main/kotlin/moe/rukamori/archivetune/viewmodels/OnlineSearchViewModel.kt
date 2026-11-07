/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.innertube.pages.SearchSummaryPage
import moe.rukamori.archivetune.models.ItemsPage
import moe.rukamori.archivetune.ui.screens.search.OnlineSearchResultArgument
import moe.rukamori.archivetune.ui.screens.search.decodeOnlineSearchQuery
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.getAsync
import moe.rukamori.archivetune.utils.reportException
import java.io.IOException
import javax.inject.Inject

enum class OnlineSearchSort {
    DEFAULT,
    VIEWS,
}

enum class SearchLoadError {
    Offline,
    Failed,
}

@HiltViewModel
class OnlineSearchViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        savedStateHandle: SavedStateHandle,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        val query =
            decodeOnlineSearchQuery(
                savedStateHandle.get<String>(OnlineSearchResultArgument).orEmpty(),
            )
        val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
        var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
            private set
        var summaryError by mutableStateOf<SearchLoadError?>(null)
            private set
        val viewStateMap = mutableStateMapOf<String, ItemsPage?>()
        val filterErrors = mutableStateMapOf<String, SearchLoadError>()
        var isLoadingMore by mutableStateOf(false)
            private set
        var loadMoreError by mutableStateOf<SearchLoadError?>(null)
            private set

        private val allModeFilters =
            listOf(
                FILTER_SONG,
                FILTER_VIDEO,
                FILTER_ALBUM,
                FILTER_ARTIST,
                FILTER_PODCAST,
                FILTER_COMMUNITY_PLAYLIST,
                FILTER_FEATURED_PLAYLIST,
            )
        private var isSummaryLoading = false
        private val loadingFilters = mutableSetOf<String>()

        val allModeResolved: Boolean
            get() =
                (summaryPage != null || summaryError != null) &&
                    allModeFilters.all { viewStateMap.containsKey(it.value) || filterErrors.containsKey(it.value) }

        init {
            viewModelScope.launch {
                filter.collect { selectedFilter ->
                    loadMoreError = null
                    if (selectedFilter == null) {
                        loadAllMode()
                    } else {
                        viewModelScope.launch {
                            loadFilterIfNeeded(selectedFilter)
                        }
                    }
                }
            }
        }

        private fun loadAllMode() {
            viewModelScope.launch {
                loadSummaryIfNeeded()
            }
            allModeFilters.forEach { allModeFilter ->
                viewModelScope.launch {
                    loadFilterIfNeeded(allModeFilter)
                }
            }
        }

        fun selectFilter(selectedFilter: YouTube.SearchFilter?) {
            if (filter.value != selectedFilter) {
                filter.value = selectedFilter
            }
        }

        fun retry() {
            val selectedFilter = filter.value
            if (selectedFilter == null) {
                summaryError = null
                filterErrors.clear()
                loadAllMode()
            } else {
                filterErrors.remove(selectedFilter.value)
                viewModelScope.launch {
                    loadFilterIfNeeded(selectedFilter)
                }
            }
        }

        private suspend fun hideExplicit() = context.dataStore.getAsync(HideExplicitKey, false)

        private suspend fun hideVideo() = context.dataStore.getAsync(HideVideoKey, false)

        private fun Throwable.toSearchLoadError(): SearchLoadError {
            if (this is CancellationException) throw this
            return if (this is IOException) SearchLoadError.Offline else SearchLoadError.Failed
        }

        private suspend fun loadSummaryIfNeeded() {
            if (summaryPage != null || isSummaryLoading) return

            isSummaryLoading = true
            summaryError = null
            try {
                YouTube
                    .searchSummary(query)
                    .onSuccess {
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        val contentFilteredPage =
                            it
                                .filterExplicit(hideExplicit())
                                .filterVideo(hideVideo())
                        summaryPage =
                            contentFilteredPage.copy(
                                summaries =
                                    contentFilteredPage.summaries.mapNotNull { summary ->
                                        summary
                                            .copy(items = filterAiContent(summary.items, aiContentFilterPolicy))
                                            .takeIf { filteredSummary -> filteredSummary.items.isNotEmpty() }
                                    },
                            )
                    }.onFailure {
                        summaryError = it.toSearchLoadError()
                        reportException(it)
                    }
            } finally {
                isSummaryLoading = false
            }
        }

        private suspend fun loadFilterIfNeeded(filter: YouTube.SearchFilter) {
            val filterKey = filter.value
            if (viewStateMap.containsKey(filterKey) || !loadingFilters.add(filterKey)) return

            filterErrors.remove(filterKey)
            try {
                YouTube
                    .search(query, filter)
                    .onSuccess { result ->
                        val aiContentFilterPolicy = loadAiContentFilterPolicy()
                        viewStateMap[filterKey] =
                            ItemsPage(
                                filterAiContent(
                                    result.items
                                        .distinctBy { it.id }
                                        .filterExplicit(hideExplicit())
                                        .filterVideo(hideVideo()),
                                    aiContentFilterPolicy,
                                ),
                                result.continuation,
                            )
                    }.onFailure {
                        filterErrors[filterKey] = it.toSearchLoadError()
                        reportException(it)
                    }
            } finally {
                loadingFilters.remove(filterKey)
            }
        }

        fun loadMore() {
            val filterKey = filter.value?.value ?: return
            if (isLoadingMore) return
            val viewState = viewStateMap[filterKey] ?: return
            val continuation = viewState.continuation ?: return

            isLoadingMore = true
            loadMoreError = null
            viewModelScope.launch {
                try {
                    YouTube
                        .searchContinuation(continuation)
                        .onSuccess { searchResult ->
                            val aiContentFilterPolicy = loadAiContentFilterPolicy()
                            val continuedItems =
                                filterAiContent(
                                    searchResult.items
                                        .filterExplicit(hideExplicit())
                                        .filterVideo(hideVideo()),
                                    aiContentFilterPolicy,
                                )
                            val current = viewStateMap[filterKey] ?: viewState
                            viewStateMap[filterKey] =
                                ItemsPage(
                                    (current.items + continuedItems).distinctBy { it.id },
                                    searchResult.continuation,
                                )
                        }.onFailure {
                            loadMoreError = it.toSearchLoadError()
                            reportException(it)
                        }
                } finally {
                    isLoadingMore = false
                }
            }
        }

        fun sortedItems(
            items: List<YTItem>,
            sort: OnlineSearchSort = OnlineSearchSort.DEFAULT,
        ): List<YTItem> =
            when (sort) {
                OnlineSearchSort.DEFAULT -> {
                    items
                }

                OnlineSearchSort.VIEWS -> {
                    items
                        .withIndex()
                        .sortedWith(
                            compareByDescending<IndexedValue<YTItem>> {
                                when (val item = it.value) {
                                    is SongItem -> item.viewCount
                                    is PlaylistItem -> item.viewCount
                                    else -> null
                                } ?: Long.MIN_VALUE
                            }.thenBy { it.index },
                        ).map { it.value }
                }
            }
    }
