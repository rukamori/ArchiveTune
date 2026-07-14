/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.downloads.ManageDownloadsUseCase
import moe.rukamori.archivetune.models.MediaMetadata
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

enum class DownloadLibraryTab {
    DOWNLOADED,
    PROGRESS,
}

sealed interface DownloadLibraryScreenState {
    data class Loading(
        val selectedTab: DownloadLibraryTab,
        val query: String,
        val isSearchActive: Boolean,
    ) : DownloadLibraryScreenState

    data class Success(
        val selectedTab: DownloadLibraryTab,
        val library: DownloadLibraryUiModel,
        val query: String,
        val isSearchActive: Boolean,
    ) : DownloadLibraryScreenState

    data class Empty(
        val selectedTab: DownloadLibraryTab,
        val query: String,
        val isSearchActive: Boolean,
    ) : DownloadLibraryScreenState

    data class Error(
        val selectedTab: DownloadLibraryTab,
        @StringRes val messageRes: Int,
        val query: String,
        val isSearchActive: Boolean,
    ) : DownloadLibraryScreenState
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
                selectedTab,
                query,
                isSearchActive,
            ) { result, tab, currentQuery, searchActive ->
                when (result) {
                    is DownloadLibraryResult.Data -> {
                        if (result.library.isEmpty) {
                            DownloadLibraryScreenState.Empty(tab, currentQuery, searchActive)
                        } else {
                            DownloadLibraryScreenState.Success(
                                selectedTab = tab,
                                library = result.library.filteredBy(currentQuery),
                                query = currentQuery,
                                isSearchActive = searchActive,
                            )
                        }
                    }

                    DownloadLibraryResult.Failure -> {
                        DownloadLibraryScreenState.Error(
                            selectedTab = tab,
                            messageRes = R.string.downloads_load_failed,
                            query = currentQuery,
                            isSearchActive = searchActive,
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

        fun open(entry: DownloadEntryUiModel) {
            val event =
                entry.destinationRoute?.let { DownloadLibraryEvent.Navigate(it) }
                    ?: entry.playbackMetadata?.let { DownloadLibraryEvent.PlaySong(it) }
                    ?: return
            eventChannel.trySend(event)
        }

        fun pause(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.pause(entry.songIds) }

        fun resume(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.resume(entry.songIds) }

        fun remove(entry: DownloadEntryUiModel) = runAction(entry.id) { manageDownloads.remove(entry.songIds) }

        fun pause(section: DownloadSectionUiModel) = runAction("section:${section.mediaType}") { manageDownloads.pause(section.songIds) }

        fun resume(section: DownloadSectionUiModel) = runAction("section:${section.mediaType}") { manageDownloads.resume(section.songIds) }

        fun remove(section: DownloadSectionUiModel) = runAction("section:${section.mediaType}") { manageDownloads.remove(section.songIds) }

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

        private companion object {
            const val PROGRESS_TAB_ARGUMENT = "progress"
        }
    }
