/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.downloads.DownloadLibraryUiModel
import moe.rukamori.archivetune.downloads.DownloadMediaType
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.downloads.ManageDownloadsUseCase
import moe.rukamori.archivetune.models.MediaMetadata
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class DownloadLibraryTab {
    DOWNLOADED,
    PROGRESS,
}

enum class DownloadLibrarySortField {
    NAME,
    DATE_MODIFIED,
}

enum class DownloadLibrarySortOrder {
    ASCENDING,
    DESCENDING,
}

@Immutable
data class DownloadLibrarySort(
    val field: DownloadLibrarySortField = DownloadLibrarySortField.DATE_MODIFIED,
    val order: DownloadLibrarySortOrder = DownloadLibrarySortOrder.DESCENDING,
)

sealed interface DownloadLibraryScreenState {
    val pendingRemoval: DownloadRemovalConfirmation?
    val sort: DownloadLibrarySort

    data class Loading(
        val selectedTab: DownloadLibraryTab,
        val query: String,
        val isSearchActive: Boolean,
        override val sort: DownloadLibrarySort,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Success(
        val selectedTab: DownloadLibraryTab,
        val library: DownloadLibraryUiModel,
        val query: String,
        val isSearchActive: Boolean,
        override val sort: DownloadLibrarySort,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Empty(
        val selectedTab: DownloadLibraryTab,
        val query: String,
        val isSearchActive: Boolean,
        override val sort: DownloadLibrarySort,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState

    data class Error(
        val selectedTab: DownloadLibraryTab,
        @StringRes val messageRes: Int,
        val query: String,
        val isSearchActive: Boolean,
        override val sort: DownloadLibrarySort,
        override val pendingRemoval: DownloadRemovalConfirmation? = null,
    ) : DownloadLibraryScreenState
}

@Immutable
sealed interface DownloadRemovalConfirmation {
    data class Entry(
        val entry: DownloadEntryUiModel,
    ) : DownloadRemovalConfirmation

    data class Section(
        val section: DownloadSectionUiModel,
    ) : DownloadRemovalConfirmation
}

sealed interface DownloadLibraryEvent {
    data class Message(
        @StringRes val messageRes: Int,
    ) : DownloadLibraryEvent

    data class Navigate(
        val route: String,
    ) : DownloadLibraryEvent

    data class PlaySong(
        val metadata: MediaMetadata,
        val queue: ImmutableList<MediaMetadata>,
        val startIndex: Int,
    ) : DownloadLibraryEvent
}

@HiltViewModel
class DownloadLibraryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val manageDownloads: ManageDownloadsUseCase,
    ) : ViewModel() {
        private val selectedTab =
            MutableStateFlow(
                if (savedStateHandle.get<String>("tab") == PROGRESS_TAB_ARGUMENT) {
                    DownloadLibraryTab.PROGRESS
                } else {
                    DownloadLibraryTab.DOWNLOADED
                },
            )
        private val query = MutableStateFlow("")
        private val isSearchActive = MutableStateFlow(false)
        private val sort = MutableStateFlow(DownloadLibrarySort())
        private val pendingRemoval = MutableStateFlow<DownloadRemovalConfirmation?>(null)
        private val actionJobs = ConcurrentHashMap<String, Job>()
        private val eventChannel = Channel<DownloadLibraryEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()
        private val screenControls =
            combine(
                selectedTab,
                query,
                isSearchActive,
                sort,
                pendingRemoval,
            ) { tab, currentQuery, searchActive, currentSort, removalConfirmation ->
                DownloadLibraryScreenControls(
                    selectedTab = tab,
                    query = currentQuery,
                    isSearchActive = searchActive,
                    sort = currentSort,
                    pendingRemoval = removalConfirmation,
                )
            }

        val screenState =
            combine(
                manageDownloads
                    .observe()
                    .map<DownloadLibraryUiModel, DownloadLibraryResult> { DownloadLibraryResult.Data(it) }
                    .catch {
                        emit(DownloadLibraryResult.Failure)
                    },
                screenControls,
            ) { result, controls ->
                when (result) {
                    is DownloadLibraryResult.Data -> {
                        if (result.library.isEmpty) {
                            DownloadLibraryScreenState.Empty(
                                selectedTab = controls.selectedTab,
                                query = controls.query,
                                isSearchActive = controls.isSearchActive,
                                sort = controls.sort,
                                pendingRemoval = controls.pendingRemoval,
                            )
                        } else {
                            DownloadLibraryScreenState.Success(
                                selectedTab = controls.selectedTab,
                                library = result.library.filteredBy(controls.query).sortedBy(controls.sort),
                                query = controls.query,
                                isSearchActive = controls.isSearchActive,
                                sort = controls.sort,
                                pendingRemoval = controls.pendingRemoval,
                            )
                        }
                    }

                    DownloadLibraryResult.Failure -> {
                        DownloadLibraryScreenState.Error(
                            selectedTab = controls.selectedTab,
                            messageRes = R.string.downloads_load_failed,
                            query = controls.query,
                            isSearchActive = controls.isSearchActive,
                            sort = controls.sort,
                            pendingRemoval = controls.pendingRemoval,
                        )
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue =
                    DownloadLibraryScreenState.Loading(
                        selectedTab = selectedTab.value,
                        query = "",
                        isSearchActive = false,
                        sort = sort.value,
                    ),
            )

        fun selectTab(tab: DownloadLibraryTab) {
            selectedTab.value = tab
        }

        fun activateSearch() {
            isSearchActive.value = true
        }

        fun closeSearch() {
            query.value = ""
            isSearchActive.value = false
        }

        fun submitSearch() {
            isSearchActive.value = false
        }

        fun updateQuery(value: String) {
            query.value = value
        }

        fun clearQuery() {
            query.value = ""
        }

        fun selectSortField(field: DownloadLibrarySortField) {
            sort.value = sort.value.copy(field = field)
        }

        fun selectSortOrder(order: DownloadLibrarySortOrder) {
            sort.value = sort.value.copy(order = order)
        }

        fun open(entry: DownloadEntryUiModel) {
            val event =
                entry.destinationRoute?.let { DownloadLibraryEvent.Navigate(it) }
                    ?: createPlaySongEvent(entry)
                    ?: return
            eventChannel.trySend(event)
        }

        private fun createPlaySongEvent(entry: DownloadEntryUiModel): DownloadLibraryEvent.PlaySong? {
            val selectedMetadata = entry.playbackMetadata ?: return null
            val downloadedSongQueue =
                currentDownloadedSongEntries()
                    .mapNotNull { queueEntry ->
                        queueEntry.playbackMetadata?.let { metadata -> queueEntry.id to metadata }
                    }
            val startIndex = downloadedSongQueue.indexOfFirst { (entryId) -> entryId == entry.id }

            return if (startIndex >= 0) {
                DownloadLibraryEvent.PlaySong(
                    metadata = selectedMetadata,
                    queue = ImmutableList.copyOf(downloadedSongQueue.map { (_, metadata) -> metadata }),
                    startIndex = startIndex,
                )
            } else {
                DownloadLibraryEvent.PlaySong(
                    metadata = selectedMetadata,
                    queue = ImmutableList.of(selectedMetadata),
                    startIndex = 0,
                )
            }
        }

        private fun currentDownloadedSongEntries(): List<DownloadEntryUiModel> {
            val state = screenState.value as? DownloadLibraryScreenState.Success ?: return emptyList()
            if (state.selectedTab != DownloadLibraryTab.DOWNLOADED) return emptyList()

            return state.library.downloadedSections
                .firstOrNull { section -> section.mediaType == DownloadMediaType.SONG }
                ?.entries
                .orEmpty()
        }

        fun pause(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.pause(entry.songIds) }

        fun resume(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.resume(entry.songIds) }

        fun remove(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.remove(entry.songIds) }

        fun pause(section: DownloadSectionUiModel) = runAction("section:${section.mediaType}") { manageDownloads.pause(section.songIds) }

        fun resume(section: DownloadSectionUiModel) = runAction("section:${section.mediaType}") { manageDownloads.resume(section.songIds) }

        fun remove(section: DownloadSectionUiModel) = runAction("section:${section.mediaType}") { manageDownloads.remove(section.songIds) }

        fun requestRemove(entry: DownloadEntryUiModel) {
            pendingRemoval.value = DownloadRemovalConfirmation.Entry(entry)
        }

        fun requestRemove(section: DownloadSectionUiModel) {
            pendingRemoval.value = DownloadRemovalConfirmation.Section(section)
        }

        fun dismissRemoveConfirmation() {
            pendingRemoval.value = null
        }

        fun confirmRemove() {
            val confirmation = pendingRemoval.value ?: return
            pendingRemoval.value = null
            when (confirmation) {
                is DownloadRemovalConfirmation.Entry -> remove(confirmation.entry)
                is DownloadRemovalConfirmation.Section -> remove(confirmation.section)
            }
        }

        private fun runAction(
            key: String,
            action: () -> Unit,
        ) {
            actionJobs.remove(key)?.cancel()
            val job =
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching(action)
                        .onFailure {
                            eventChannel.send(DownloadLibraryEvent.Message(R.string.download_action_failed))
                        }
                }
            actionJobs[key] = job
            job.invokeOnCompletion { actionJobs.remove(key, job) }
        }

        private sealed interface DownloadLibraryResult {
            data class Data(
                val library: DownloadLibraryUiModel,
            ) : DownloadLibraryResult

            data object Failure : DownloadLibraryResult
        }

        private data class DownloadLibraryScreenControls(
            val selectedTab: DownloadLibraryTab,
            val query: String,
            val isSearchActive: Boolean,
            val sort: DownloadLibrarySort,
            val pendingRemoval: DownloadRemovalConfirmation?,
        )

        private fun DownloadLibraryUiModel.filteredBy(query: String): DownloadLibraryUiModel {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isEmpty()) return this

            fun List<DownloadSectionUiModel>.filteredSections(): List<DownloadSectionUiModel> =
                mapNotNull { section ->
                    section.entries
                        .filter { entry ->
                            entry.title.contains(normalizedQuery, ignoreCase = true) ||
                                entry.supportingText.orEmpty().contains(normalizedQuery, ignoreCase = true)
                        }.takeIf { it.isNotEmpty() }
                        ?.let { entries -> section.copy(entries = entries) }
                }

            return copy(
                downloadedSections = downloadedSections.filteredSections(),
                progressSections = progressSections.filteredSections(),
            )
        }

        private fun DownloadLibraryUiModel.sortedBy(sort: DownloadLibrarySort): DownloadLibraryUiModel {
            fun List<DownloadEntryUiModel>.sortedEntries(): List<DownloadEntryUiModel> {
                val comparator: Comparator<DownloadEntryUiModel> =
                    when (sort.field) {
                        DownloadLibrarySortField.NAME ->
                            Comparator { first, second ->
                                String.CASE_INSENSITIVE_ORDER.compare(first.title, second.title)
                            }

                        DownloadLibrarySortField.DATE_MODIFIED -> compareBy(DownloadEntryUiModel::modifiedAtMs)
                    }.thenBy(DownloadEntryUiModel::id)
                val sortedEntries = sortedWith(comparator)
                return if (sort.order == DownloadLibrarySortOrder.ASCENDING) sortedEntries else sortedEntries.asReversed()
            }

            fun List<DownloadSectionUiModel>.sortedSections(): List<DownloadSectionUiModel> =
                map { section ->
                    section.copy(
                        entries = section.entries.sortedEntries(),
                    )
                }

            return copy(
                downloadedSections = downloadedSections.sortedSections(),
                progressSections = progressSections.sortedSections(),
            )
        }

        private companion object {
            const val PROGRESS_TAB_ARGUMENT = "progress"
        }
    }
