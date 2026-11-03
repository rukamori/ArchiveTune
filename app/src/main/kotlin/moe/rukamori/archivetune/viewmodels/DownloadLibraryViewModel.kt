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
import kotlinx.coroutines.flow.update
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

@Immutable
data class DownloadLibraryControls(
    val selectedTab: DownloadLibraryTab,
    val query: String = "",
    val isSearchActive: Boolean = false,
    val sort: DownloadLibrarySort = DownloadLibrarySort(),
    val typeFilter: DownloadMediaType? = null,
    val selection: Set<String> = emptySet(),
    val pendingRemoval: DownloadRemovalConfirmation? = null,
) {
    val hasActiveFilter: Boolean = query.isNotBlank() || typeFilter != null
}

sealed interface DownloadLibraryScreenState {
    val controls: DownloadLibraryControls

    data class Loading(
        override val controls: DownloadLibraryControls,
    ) : DownloadLibraryScreenState

    data class Success(
        override val controls: DownloadLibraryControls,
        val library: DownloadLibraryUiModel,
    ) : DownloadLibraryScreenState {
        val downloadedSections: List<DownloadSectionUiModel> = library.downloadedSections.ofType(controls.typeFilter)
        val progressSections: List<DownloadSectionUiModel> = library.progressSections.ofType(controls.typeFilter)
        val selectedEntries: List<DownloadEntryUiModel> =
            sectionsFor(controls.selectedTab)
                .flatMap(DownloadSectionUiModel::entries)
                .filter { entry -> entry.id in controls.selection }
        val allVisibleSelected: Boolean =
            selectedEntries.isNotEmpty() &&
                selectedEntries.size == sectionsFor(controls.selectedTab).sumOf { it.entries.size }

        fun sectionsFor(tab: DownloadLibraryTab): List<DownloadSectionUiModel> =
            if (tab == DownloadLibraryTab.PROGRESS) progressSections else downloadedSections
    }

    data class Empty(
        override val controls: DownloadLibraryControls,
    ) : DownloadLibraryScreenState

    data class Error(
        override val controls: DownloadLibraryControls,
        @StringRes val messageRes: Int,
    ) : DownloadLibraryScreenState
}

@Immutable
sealed interface DownloadRemovalConfirmation {
    val cancelsActiveDownloads: Boolean

    data class Entry(
        val entry: DownloadEntryUiModel,
    ) : DownloadRemovalConfirmation {
        override val cancelsActiveDownloads: Boolean = false
    }

    data class Section(
        val section: DownloadSectionUiModel,
        override val cancelsActiveDownloads: Boolean,
    ) : DownloadRemovalConfirmation

    data class Selection(
        val entries: List<DownloadEntryUiModel>,
        override val cancelsActiveDownloads: Boolean,
    ) : DownloadRemovalConfirmation

    data class Queue(
        val songIds: List<String>,
    ) : DownloadRemovalConfirmation {
        override val cancelsActiveDownloads: Boolean = true
    }
}

sealed interface DownloadLibraryEvent {
    data class Message(
        @StringRes val messageRes: Int,
    ) : DownloadLibraryEvent

    data class Navigate(
        val route: String,
    ) : DownloadLibraryEvent

    data class PlayQueue(
        val title: String?,
        @StringRes val titleRes: Int?,
        val queue: ImmutableList<MediaMetadata>,
        val startIndex: Int,
    ) : DownloadLibraryEvent

    data class PlayNext(
        val items: ImmutableList<MediaMetadata>,
    ) : DownloadLibraryEvent

    data class AddToQueue(
        val items: ImmutableList<MediaMetadata>,
    ) : DownloadLibraryEvent
}

@HiltViewModel
class DownloadLibraryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val manageDownloads: ManageDownloadsUseCase,
    ) : ViewModel() {
        private val controls =
            MutableStateFlow(
                DownloadLibraryControls(
                    selectedTab =
                        if (savedStateHandle.get<String>("tab") == PROGRESS_TAB_ARGUMENT) {
                            DownloadLibraryTab.PROGRESS
                        } else {
                            DownloadLibraryTab.DOWNLOADED
                        },
                ),
            )
        private val actionJobs = ConcurrentHashMap<String, Job>()
        private val eventChannel = Channel<DownloadLibraryEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        val screenState =
            combine(
                manageDownloads
                    .observe()
                    .map<DownloadLibraryUiModel, DownloadLibraryResult> { DownloadLibraryResult.Data(it) }
                    .catch {
                        emit(DownloadLibraryResult.Failure)
                    },
                controls,
            ) { result, currentControls ->
                when (result) {
                    is DownloadLibraryResult.Data -> {
                        if (result.library.isEmpty) {
                            DownloadLibraryScreenState.Empty(currentControls)
                        } else {
                            DownloadLibraryScreenState.Success(
                                controls = currentControls,
                                library = result.library.filteredBy(currentControls.query).sortedBy(currentControls.sort),
                            )
                        }
                    }

                    DownloadLibraryResult.Failure -> {
                        DownloadLibraryScreenState.Error(
                            controls = currentControls,
                            messageRes = R.string.downloads_load_failed,
                        )
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = DownloadLibraryScreenState.Loading(controls.value),
            )

        fun selectTab(tab: DownloadLibraryTab) {
            controls.update { current ->
                if (current.selectedTab == tab) current else current.copy(selectedTab = tab, selection = emptySet())
            }
        }

        fun activateSearch() {
            controls.update { it.copy(isSearchActive = true) }
        }

        fun closeSearch() {
            controls.update { it.copy(query = "", isSearchActive = false) }
        }

        fun updateQuery(value: String) {
            controls.update { it.copy(query = value) }
        }

        fun clearQuery() {
            controls.update { it.copy(query = "") }
        }

        fun clearFilters() {
            controls.update { it.copy(query = "", isSearchActive = false, typeFilter = null) }
        }

        fun selectTypeFilter(type: DownloadMediaType?) {
            controls.update { it.copy(typeFilter = type, selection = emptySet()) }
        }

        fun selectSortField(field: DownloadLibrarySortField) {
            controls.update { it.copy(sort = it.sort.copy(field = field)) }
        }

        fun selectSortOrder(order: DownloadLibrarySortOrder) {
            controls.update { it.copy(sort = it.sort.copy(order = order)) }
        }

        fun toggleSelection(entry: DownloadEntryUiModel) {
            controls.update { current ->
                val selection =
                    if (entry.id in current.selection) current.selection - entry.id else current.selection + entry.id
                current.copy(selection = selection)
            }
        }

        fun toggleSelectAll() {
            val state = successState() ?: return
            controls.update { current ->
                if (state.allVisibleSelected) {
                    current.copy(selection = emptySet())
                } else {
                    current.copy(
                        selection =
                            state
                                .sectionsFor(current.selectedTab)
                                .flatMap(DownloadSectionUiModel::entries)
                                .mapTo(mutableSetOf(), DownloadEntryUiModel::id),
                    )
                }
            }
        }

        fun selectSection(section: DownloadSectionUiModel) {
            controls.update { current -> current.copy(selection = current.selection + section.entries.map(DownloadEntryUiModel::id)) }
        }

        fun clearSelection() {
            controls.update { it.copy(selection = emptySet()) }
        }

        fun open(entry: DownloadEntryUiModel) {
            val event =
                entry.destinationRoute?.let { DownloadLibraryEvent.Navigate(it) }
                    ?: createPlaySongEvent(entry)
                    ?: return
            eventChannel.trySend(event)
        }

        fun play(
            entry: DownloadEntryUiModel,
            shuffle: Boolean,
        ) {
            if (entry.destinationRoute == null && !shuffle) {
                open(entry)
                return
            }
            sendPlayQueue(entry.playableSongs(), title = entry.title, titleRes = null, shuffle = shuffle)
        }

        fun playChild(
            parent: DownloadEntryUiModel,
            child: DownloadEntryUiModel,
        ) {
            val queue = parent.playableSongs()
            val startIndex = queue.indexOfFirst { metadata -> metadata.id == child.playbackMetadata?.id }
            if (startIndex < 0) return
            eventChannel.trySend(
                DownloadLibraryEvent.PlayQueue(
                    title = parent.title,
                    titleRes = null,
                    queue = ImmutableList.copyOf(queue),
                    startIndex = startIndex,
                ),
            )
        }

        fun playNext(entry: DownloadEntryUiModel) = sendPlayNext(listOf(entry))

        fun addToQueue(entry: DownloadEntryUiModel) = sendAddToQueue(listOf(entry))

        fun playAll(shuffle: Boolean) {
            sendPlayQueue(
                songs = currentDownloadedSongEntries().mapNotNull(DownloadEntryUiModel::playbackMetadata),
                title = null,
                titleRes = R.string.downloads,
                shuffle = shuffle,
            )
        }

        fun playSection(
            section: DownloadSectionUiModel,
            shuffle: Boolean,
        ) {
            sendPlayQueue(
                songs = section.entries.flatMap { it.playableSongs() }.distinctBy(MediaMetadata::id),
                title = null,
                titleRes = R.string.downloads,
                shuffle = shuffle,
            )
        }

        fun playSelection(shuffle: Boolean) {
            val entries = successState()?.selectedEntries.orEmpty()
            sendPlayQueue(
                songs = entries.flatMap { it.playableSongs() }.distinctBy(MediaMetadata::id),
                title = null,
                titleRes = R.string.downloads,
                shuffle = shuffle,
            )
            clearSelection()
        }

        fun playSelectionNext() {
            sendPlayNext(successState()?.selectedEntries.orEmpty())
            clearSelection()
        }

        fun addSelectionToQueue() {
            sendAddToQueue(successState()?.selectedEntries.orEmpty())
            clearSelection()
        }

        fun pause(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.pause(entry.songIds) }

        fun resume(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.resume(entry.songIds) }

        fun remove(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.remove(entry.songIds) }

        fun pause(section: DownloadSectionUiModel) = runAction(section.actionKey()) { manageDownloads.pause(section.songIds) }

        fun resume(section: DownloadSectionUiModel) = runAction(section.actionKey()) { manageDownloads.resume(section.songIds) }

        fun pauseSelection() {
            val songIds = successState()?.selectedEntries.orEmpty().selectedSongIds()
            clearSelection()
            if (songIds.isNotEmpty()) runAction(SELECTION_ACTION_KEY) { manageDownloads.pause(songIds) }
        }

        fun resumeSelection() {
            val songIds = successState()?.selectedEntries.orEmpty().selectedSongIds()
            clearSelection()
            if (songIds.isNotEmpty()) runAction(SELECTION_ACTION_KEY) { manageDownloads.resume(songIds) }
        }

        fun pauseAll() {
            val songIds = successState()?.library?.queue?.songIds.orEmpty()
            if (songIds.isNotEmpty()) runAction(QUEUE_ACTION_KEY) { manageDownloads.pause(songIds) }
        }

        fun resumeAll() {
            val songIds = successState()?.library?.queue?.songIds.orEmpty()
            if (songIds.isNotEmpty()) runAction(QUEUE_ACTION_KEY) { manageDownloads.resume(songIds) }
        }

        fun retryFailed() {
            val songIds = successState()?.library?.queue?.failedSongIds.orEmpty()
            if (songIds.isNotEmpty()) runAction(QUEUE_ACTION_KEY) { manageDownloads.resume(songIds) }
        }

        fun requestRemove(entry: DownloadEntryUiModel) {
            controls.update { it.copy(pendingRemoval = DownloadRemovalConfirmation.Entry(entry)) }
        }

        fun requestRemove(section: DownloadSectionUiModel) {
            val inProgress = controls.value.selectedTab == DownloadLibraryTab.PROGRESS
            controls.update {
                it.copy(pendingRemoval = DownloadRemovalConfirmation.Section(section, cancelsActiveDownloads = inProgress))
            }
        }

        fun requestRemoveSelection() {
            val state = successState() ?: return
            if (state.selectedEntries.isEmpty()) return
            controls.update {
                it.copy(
                    pendingRemoval =
                        DownloadRemovalConfirmation.Selection(
                            entries = state.selectedEntries,
                            cancelsActiveDownloads = it.selectedTab == DownloadLibraryTab.PROGRESS,
                        ),
                )
            }
        }

        fun requestCancelAll() {
            val songIds = successState()?.library?.queue?.songIds.orEmpty()
            if (songIds.isEmpty()) return
            controls.update { it.copy(pendingRemoval = DownloadRemovalConfirmation.Queue(songIds)) }
        }

        fun dismissRemoveConfirmation() {
            controls.update { it.copy(pendingRemoval = null) }
        }

        fun confirmRemove() {
            val confirmation = controls.value.pendingRemoval ?: return
            controls.update { it.copy(pendingRemoval = null) }
            when (confirmation) {
                is DownloadRemovalConfirmation.Entry -> {
                    remove(confirmation.entry)
                }

                is DownloadRemovalConfirmation.Section -> {
                    runAction(confirmation.section.actionKey()) { manageDownloads.remove(confirmation.section.songIds) }
                }

                is DownloadRemovalConfirmation.Selection -> {
                    val songIds = confirmation.entries.selectedSongIds()
                    clearSelection()
                    runAction(SELECTION_ACTION_KEY) { manageDownloads.remove(songIds) }
                }

                is DownloadRemovalConfirmation.Queue -> {
                    runAction(QUEUE_ACTION_KEY) { manageDownloads.remove(confirmation.songIds) }
                }
            }
        }

        private fun successState(): DownloadLibraryScreenState.Success? = screenState.value as? DownloadLibraryScreenState.Success

        private fun createPlaySongEvent(entry: DownloadEntryUiModel): DownloadLibraryEvent.PlayQueue? {
            val selectedMetadata = entry.playbackMetadata ?: return null
            val downloadedSongQueue =
                currentDownloadedSongEntries()
                    .mapNotNull { queueEntry ->
                        queueEntry.playbackMetadata?.let { metadata -> queueEntry.id to metadata }
                    }
            val startIndex = downloadedSongQueue.indexOfFirst { (entryId) -> entryId == entry.id }

            return if (startIndex >= 0) {
                DownloadLibraryEvent.PlayQueue(
                    title = null,
                    titleRes = R.string.downloads,
                    queue = ImmutableList.copyOf(downloadedSongQueue.map { (_, metadata) -> metadata }),
                    startIndex = startIndex,
                )
            } else {
                DownloadLibraryEvent.PlayQueue(
                    title = selectedMetadata.title,
                    titleRes = null,
                    queue = ImmutableList.of(selectedMetadata),
                    startIndex = 0,
                )
            }
        }

        private fun currentDownloadedSongEntries(): List<DownloadEntryUiModel> {
            val state = successState() ?: return emptyList()
            if (state.controls.selectedTab != DownloadLibraryTab.DOWNLOADED) return emptyList()

            return state.library.downloadedSections
                .firstOrNull { section -> section.mediaType == DownloadMediaType.SONG }
                ?.entries
                .orEmpty()
        }

        private fun sendPlayQueue(
            songs: List<MediaMetadata>,
            title: String?,
            @StringRes titleRes: Int?,
            shuffle: Boolean,
        ) {
            if (songs.isEmpty()) {
                eventChannel.trySend(DownloadLibraryEvent.Message(R.string.download_nothing_to_play))
                return
            }
            eventChannel.trySend(
                DownloadLibraryEvent.PlayQueue(
                    title = title,
                    titleRes = titleRes,
                    queue = ImmutableList.copyOf(if (shuffle) songs.shuffled() else songs),
                    startIndex = 0,
                ),
            )
        }

        private fun sendPlayNext(entries: List<DownloadEntryUiModel>) {
            val songs = entries.flatMap { it.playableSongs() }.distinctBy(MediaMetadata::id)
            eventChannel.trySend(
                if (songs.isEmpty()) {
                    DownloadLibraryEvent.Message(R.string.download_nothing_to_play)
                } else {
                    DownloadLibraryEvent.PlayNext(ImmutableList.copyOf(songs))
                },
            )
        }

        private fun sendAddToQueue(entries: List<DownloadEntryUiModel>) {
            val songs = entries.flatMap { it.playableSongs() }.distinctBy(MediaMetadata::id)
            eventChannel.trySend(
                if (songs.isEmpty()) {
                    DownloadLibraryEvent.Message(R.string.download_nothing_to_play)
                } else {
                    DownloadLibraryEvent.AddToQueue(ImmutableList.copyOf(songs))
                },
            )
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

        private fun DownloadEntryUiModel.playableSongs(): List<MediaMetadata> =
            playbackMetadata?.let(::listOf) ?: children.mapNotNull(DownloadEntryUiModel::playbackMetadata)

        private fun List<DownloadEntryUiModel>.selectedSongIds(): List<String> = flatMap(DownloadEntryUiModel::songIds).distinct()

        private fun DownloadSectionUiModel.actionKey(): String = "section:$mediaType"

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
            const val SELECTION_ACTION_KEY = "selection"
            const val QUEUE_ACTION_KEY = "queue"
        }
    }

private fun List<DownloadSectionUiModel>.ofType(type: DownloadMediaType?): List<DownloadSectionUiModel> =
    if (type == null) this else filter { section -> section.mediaType == type }
