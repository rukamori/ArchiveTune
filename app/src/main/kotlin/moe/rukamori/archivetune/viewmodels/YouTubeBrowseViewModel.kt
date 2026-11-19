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
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.browse.GenreBrowseScreenState
import moe.rukamori.archivetune.browse.GenreBrowseUiState
import moe.rukamori.archivetune.browse.GenreCollection
import moe.rukamori.archivetune.browse.GenreSource
import moe.rukamori.archivetune.browse.GenreSourceState
import moe.rukamori.archivetune.browse.GenreTab
import moe.rukamori.archivetune.browse.LoadGenreDiscoveryUseCase
import moe.rukamori.archivetune.browse.genreKey
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class YouTubeBrowseViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val loadGenreDiscovery: LoadGenreDiscoveryUseCase,
) : ViewModel() {
    private val endpoint = BrowseEndpoint(
        browseId = requireNotNull(savedStateHandle.get<String>("browseId")),
        params = savedStateHandle.get<String>("params"),
    )
    private val genreTitle = savedStateHandle.get<String>("genreTitle")?.takeIf(String::isNotBlank)
    private val sourceRequests = mutableSetOf<GenreSource>()
    private val collectionRequests = mutableSetOf<String>()
    private val mutableState = MutableStateFlow<GenreBrowseScreenState>(
        GenreBrowseScreenState.Loading(
            GenreBrowseUiState(
                genreTitle = genreTitle,
                sources = ImmutableList.copyOf(
                    (if (genreTitle == null) listOf(GenreSource.FEED) else GenreSource.entries)
                        .map { GenreSourceState(source = it) },
                ),
                selectedTab = GenreTab.entries.firstOrNull {
                    it.name == savedStateHandle.get<String>("genreTab")
                } ?: GenreTab.ALL,
                expandedSection = savedStateHandle.get<String>("genreSection"),
            ),
        ),
    )
    val screenState = mutableState.asStateFlow()

    init {
        mutableState.value.data.sources.forEach { loadSource(it.source) }
    }

    fun selectTab(tab: GenreTab) {
        savedStateHandle["genreTab"] = tab.name
        savedStateHandle["genreSection"] = null
        update { it.copy(selectedTab = tab, expandedSection = null) }
    }

    fun closeSection() {
        savedStateHandle["genreSection"] = null
        update { it.copy(expandedSection = null) }
    }

    fun openSection(id: String) {
        savedStateHandle["genreSection"] = id
        update { it.copy(expandedSection = id) }
        ensureCollection(id)
    }

    fun loadSource(source: GenreSource) {
        val current = mutableState.value.data.sources.firstOrNull { it.source == source } ?: return
        if (current.loaded && current.continuation == null || !sourceRequests.add(source)) return
        val token = if (current.loaded) current.continuation else null
        updateSource(source) { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                loadGenreDiscovery(source, endpoint, genreTitle, token)
                    .onSuccess { page ->
                        updateSource(source) { previous ->
                            val sections = if (source == GenreSource.FEED) {
                                val ids = previous.sections.mapTo(HashSet()) { it.id }
                                previous.sections + page.sections.map { section ->
                                    var id = section.id
                                    var suffix = 1
                                    while (!ids.add(id)) id = "${section.id}:${suffix++}"
                                    section.copy(id = id)
                                }
                            } else {
                                page.sections.firstOrNull()?.let { section ->
                                    listOf(section.copy(items = ImmutableList.copyOf(
                                        (previous.sections.flatMap { it.items } + page.sections.flatMap { it.items })
                                            .distinctBy { it.genreKey() },
                                    )))
                                }.orEmpty()
                            }
                            previous.copy(
                                sections = ImmutableList.copyOf(sections),
                                title = page.title ?: previous.title,
                                thumbnail = page.thumbnail ?: previous.thumbnail,
                                continuation = page.continuation,
                                loading = false,
                                loaded = true,
                                failed = false,
                            )
                        }
                        if (source == GenreSource.FEED) {
                            mutableState.value.data.expandedSection?.let(::ensureCollection)
                        }
                    }.onFailure { exception ->
                        reportException(exception)
                        updateSource(source) { it.copy(loading = false, failed = true) }
                    }
            } finally {
                sourceRequests.remove(source)
            }
        }
    }

    private fun ensureCollection(id: String) {
        val state = mutableState.value.data
        val section = state.feed.sections.firstOrNull { it.id == id } ?: return
        if (state.collections.any { it.sectionId == id }) return
        val collection = GenreCollection(
            sectionId = id,
            items = section.items,
            continuations = ImmutableList.copyOf(listOfNotNull(section.continuation)),
            endpointLoaded = section.endpoint == null,
        )
        update { it.copy(collections = ImmutableList.copyOf(it.collections + collection)) }
        if (!collection.endpointLoaded) loadCollection(id)
    }

    fun loadCollection(id: String) {
        val state = mutableState.value.data
        val section = state.feed.sections.firstOrNull { it.id == id } ?: return
        val collection = state.collections.firstOrNull { it.sectionId == id } ?: return
        if (collection.endpointLoaded && collection.continuations.isEmpty() || !collectionRequests.add(id)) return
        val token = if (collection.endpointLoaded) collection.continuations.first() else null
        updateCollection(id) { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            try {
                loadGenreDiscovery(GenreSource.FEED, section.endpoint ?: endpoint, genreTitle, token)
                    .onSuccess { page ->
                        updateCollection(id) { previous ->
                            val remaining = if (token == null) emptyList() else previous.continuations.drop(1)
                            val continuations = remaining + listOfNotNull(page.continuation) + page.sections.mapNotNull { it.continuation }
                            previous.copy(
                                items = ImmutableList.copyOf(
                                    (previous.items + page.sections.flatMap { it.items }).distinctBy { it.genreKey() },
                                ),
                                continuations = ImmutableList.copyOf(continuations.distinct()),
                                endpointLoaded = true,
                                loading = false,
                                failed = false,
                            )
                        }
                    }.onFailure { exception ->
                        reportException(exception)
                        updateCollection(id) { it.copy(loading = false, failed = true) }
                    }
            } finally {
                collectionRequests.remove(id)
            }
        }
    }

    private fun updateSource(source: GenreSource, transform: (GenreSourceState) -> GenreSourceState) {
        update { data -> data.copy(sources = ImmutableList.copyOf(data.sources.map { if (it.source == source) transform(it) else it })) }
    }

    private fun updateCollection(id: String, transform: (GenreCollection) -> GenreCollection) {
        update { data -> data.copy(collections = ImmutableList.copyOf(data.collections.map { if (it.sectionId == id) transform(it) else it })) }
    }

    private fun update(transform: (GenreBrowseUiState) -> GenreBrowseUiState) {
        mutableState.update { previous ->
            val data = transform(previous.data)
            when {
                data.sources.any { source -> source.sections.any { it.items.isNotEmpty() } } ||
                    data.collections.any { it.items.isNotEmpty() } -> GenreBrowseScreenState.Success(data)
                data.sources.any { it.loading } -> GenreBrowseScreenState.Loading(data)
                data.sources.any { it.failed } -> GenreBrowseScreenState.Error(data)
                else -> GenreBrowseScreenState.Empty(data)
            }
        }
    }
}
