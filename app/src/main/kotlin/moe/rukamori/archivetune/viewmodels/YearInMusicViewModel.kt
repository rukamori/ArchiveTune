/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.recap.ObserveRecapUseCase
import moe.rukamori.archivetune.recap.RecapActionsUseCase
import moe.rukamori.archivetune.recap.RecapChapter
import moe.rukamori.archivetune.recap.RecapData
import moe.rukamori.archivetune.recap.RecapEntry
import moe.rukamori.archivetune.recap.RecapEntryKind
import moe.rukamori.archivetune.recap.RecapMenu
import moe.rukamori.archivetune.recap.RecapMonthState
import moe.rukamori.archivetune.recap.RecapSelection
import moe.rukamori.archivetune.utils.reportException

sealed interface YearInMusicUiState {
    val selection: RecapSelection

    @Immutable data class Loading(override val selection: RecapSelection) : YearInMusicUiState
    @Immutable data class Success(
        override val selection: RecapSelection,
        val data: RecapData,
        val monthState: RecapMonthState = RecapMonthState.Loading,
        val storyData: RecapData? = null,
    ) : YearInMusicUiState
    @Immutable data class Empty(override val selection: RecapSelection) : YearInMusicUiState
    @Immutable data class Error(override val selection: RecapSelection, val messageRes: Int) : YearInMusicUiState
}

sealed interface RecapEvent {
    data class OpenArtist(val id: String) : RecapEvent
    data class OpenAlbum(val id: String) : RecapEvent
    data class ShowMenu(val menu: RecapMenu) : RecapEvent
    data class Share(val uri: Uri, val request: Long) : RecapEvent
}

@HiltViewModel
class YearInMusicViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val observeRecap: ObserveRecapUseCase,
    private val actions: RecapActionsUseCase,
) : ViewModel() {
    private val currentYear: Int
        get() = LocalDate.now().year
    private var calendarDate = LocalDate.now()
    private val initialYear = (savedState.get<Int>("recap_year") ?: savedState.get<Int>("year"))
        ?.takeIf { it in 1..currentYear } ?: currentYear
    private val initialSelection = RecapSelection(
        year = initialYear,
        years = ImmutableList.of(initialYear),
        story = savedState["recap_story"] ?: false,
        chapter = savedState.get<String>("recap_chapter")?.let { name -> RecapChapter.entries.find { it.name == name } }
            ?: RecapChapter.INTRO,
        month = savedState.get<Int>("recap_month")?.takeIf { it in 1..12 },
    )
    private val state = MutableStateFlow<YearInMusicUiState>(YearInMusicUiState.Loading(initialSelection))
    val uiState: StateFlow<YearInMusicUiState> = state.asStateFlow()
    private val eventChannel = Channel<RecapEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var yearJob: Job? = null
    private var monthJob: Job? = null
    private var actionJob: Job? = null
    private var shareJob: Job? = null
    private var shareRequest = 0L
    private var soundtrackJob: Job? = null

    init {
        reload()
        viewModelScope.launch {
            actions.playback().collect { playback -> updateSelection { it.copy(playback = playback) } }
        }
    }

    fun attachPlayer(connection: PlayerConnection) = actions.attach(connection)
    fun detachPlayer(connection: PlayerConnection) = actions.detach(connection)
    fun releaseStoryMute() = actions.releaseMute()
    fun toggleStoryMute() = actions.toggleMute()

    override fun onCleared() {
        actions.releaseMute()
        super.onCleared()
    }

    fun initialize(year: Int?) {
        if (savedState.get<Boolean>("recap_initialized") == true) return
        savedState["recap_initialized"] = true
        if (year != null && year in 1..currentYear && year != state.value.selection.year) selectYear(year)
    }

    fun refreshCalendar() {
        val today = LocalDate.now()
        if (calendarDate == today) return
        calendarDate = today
        reload()
    }

    fun back(): Boolean {
        val selection = state.value.selection
        when {
            selection.yearPickerOpen -> showYearPicker(false)
            selection.story -> showStory(false)
            selection.month != null -> selectMonth(null)
            else -> return false
        }
        return true
    }

    fun reload() {
        yearJob?.cancel()
        monthJob?.cancel()
        monthJob = null
        val year = state.value.selection.year
        if (state.value !is YearInMusicUiState.Success) {
            state.value = YearInMusicUiState.Loading(state.value.selection.copy(actionMessage = null))
        }
        yearJob = viewModelScope.launch {
            observeRecap(year).catch { failure ->
                if (failure is CancellationException) throw failure
                reportException(failure)
                if ((state.value as? YearInMusicUiState.Success)?.storyData != null) {
                    updateSelection { it.copy(actionMessage = R.string.recap_load_error) }
                } else {
                    savedState["recap_story"] = false
                    actions.releaseMute()
                    state.value = YearInMusicUiState.Error(state.value.selection.copy(story = false), R.string.recap_load_error)
                }
            }.collect { result ->
                if (year !in result.years) {
                    selectYear(result.years.first())
                    return@collect
                }
                val previous = state.value as? YearInMusicUiState.Success
                val snapshot = if (state.value.selection.story) previous?.storyData ?: result.data.takeIf { it.milliseconds > 0L } else null
                val selection = state.value.selection.copy(
                    story = state.value.selection.story && snapshot != null,
                    years = result.years,
                    chapter = state.value.selection.chapter.takeIf { it in (snapshot ?: result.data).chapters } ?: RecapChapter.INTRO,
                    month = state.value.selection.month?.takeIf { requested ->
                        result.data.months.any { it.month == requested && it.available }
                    },
                )
                state.value = if (result.data.milliseconds == 0L && snapshot == null) {
                    cancelShare()
                    actions.releaseMute()
                    monthJob?.cancel()
                    monthJob = null
                    savedState["recap_story"] = false
                    savedState["recap_month"] = null
                    YearInMusicUiState.Empty(selection.copy(story = false, month = null, sharing = false))
                } else {
                    YearInMusicUiState.Success(selection, result.data,
                        previous?.monthState ?: RecapMonthState.Loading, snapshot)
                }
                if (selection.month != null && monthJob == null && state.value is YearInMusicUiState.Success) loadMonth(selection.month)
            }
        }
    }

    fun selectYear(year: Int) {
        if (year !in 1..currentYear) return
        if (year == state.value.selection.year) {
            showYearPicker(false)
            return
        }
        cancelShare()
        soundtrackJob?.cancel()
        actions.releaseMute()
        actionJob?.cancel()
        monthJob?.cancel()
        monthJob = null
        savedState["recap_year"] = year
        savedState["recap_month"] = null
        savedState["recap_story"] = false
        savedState["recap_chapter"] = RecapChapter.INTRO.name
        state.value = YearInMusicUiState.Loading(state.value.selection.copy(year = year, story = false, month = null,
            chapter = RecapChapter.INTRO, yearPickerOpen = false, sharing = false,
            soundtrackStarting = false, soundtrackFailed = false))
        reload()
    }

    fun showYearPicker(show: Boolean) = updateSelection { it.copy(yearPickerOpen = show) }

    fun showStory(show: Boolean) {
        val success = state.value as? YearInMusicUiState.Success ?: return
        if (show == success.selection.story) return
        if (show && (success.data.milliseconds == 0L || success.selection.month != null)) return
        cancelShare()
        soundtrackJob?.cancel()
        actions.releaseMute()
        savedState["recap_story"] = show
        savedState["recap_chapter"] = RecapChapter.INTRO.name
        state.update { current ->
            if (current is YearInMusicUiState.Success) {
                val selection = current.selection.copy(story = show, chapter = RecapChapter.INTRO,
                    soundtrackStarting = false, soundtrackFailed = false)
                if (!show && current.data.milliseconds == 0L) YearInMusicUiState.Empty(selection)
                else current.copy(selection = selection, storyData = if (show) success.data else null)
            } else current
        }
        if (show) retrySoundtrack()
    }

    fun replayStory() = selectChapter(RecapChapter.INTRO)

    fun retrySoundtrack() {
        val success = state.value as? YearInMusicUiState.Success ?: return
        val snapshot = success.storyData ?: return
        if (!success.selection.story || soundtrackJob?.isActive == true) return
        updateSelection { it.copy(soundtrackStarting = true, soundtrackFailed = false) }
        soundtrackJob = viewModelScope.launch {
            try {
                actions.startSoundtrack(snapshot, snapshot.year.toString())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reportException(failure)
                updateSelection { it.copy(soundtrackFailed = true) }
            } finally {
                if (currentCoroutineContext().isActive) updateSelection { it.copy(soundtrackStarting = false) }
            }
        }
    }

    fun selectChapter(chapter: RecapChapter) {
        val success = state.value as? YearInMusicUiState.Success ?: return
        if (chapter !in (success.storyData ?: success.data).chapters) return
        if (chapter == state.value.selection.chapter) return
        cancelShare()
        savedState["recap_chapter"] = chapter.name
        updateSelection { it.copy(chapter = chapter) }
    }

    fun selectMonth(month: Int?) {
        val success = state.value as? YearInMusicUiState.Success ?: return
        if (month != null && success.data.months.none { it.month == month && it.available }) return
        cancelShare()
        actionJob?.cancel()
        monthJob?.cancel()
        monthJob = null
        savedState["recap_month"] = month
        updateSelection { it.copy(month = month) }
        if (month != null) loadMonth(month)
    }

    fun reloadMonth() { state.value.selection.month?.let { selectMonth(it) } }

    private fun loadMonth(month: Int) {
        val year = state.value.selection.year
        state.update { current -> (current as? YearInMusicUiState.Success)?.copy(monthState = RecapMonthState.Loading) ?: current }
        monthJob = viewModelScope.launch {
            observeRecap(year, month).catch { failure ->
                if (failure is CancellationException) throw failure
                reportException(failure)
                updateMonth(year, month, RecapMonthState.Error(R.string.recap_load_error))
            }.collect { result ->
                updateMonth(year, month, if (result.data.milliseconds == 0L) RecapMonthState.Empty else RecapMonthState.Success(result.data))
            }
        }
    }

    private fun updateMonth(year: Int, month: Int, result: RecapMonthState) {
        state.update { current ->
            if (current is YearInMusicUiState.Success && current.selection.year == year && current.selection.month == month) {
                current.copy(monthState = result)
            } else current
        }
    }

    fun openEntry(entry: RecapEntry) {
        when (entry.kind) {
            RecapEntryKind.SONG -> play(entry.id)
            RecapEntryKind.ARTIST -> runAction { eventChannel.send(RecapEvent.OpenArtist(entry.id)) }
            RecapEntryKind.ALBUM -> runAction { eventChannel.send(RecapEvent.OpenAlbum(entry.id)) }
        }
    }

    fun showMenu(entry: RecapEntry) = runAction {
        eventChannel.send(RecapEvent.ShowMenu(actions.menu(entry)))
    }

    fun play(songId: String? = null) {
        val data = currentData() ?: return
        runAction { actions.play(data, songId, data.year.toString()) }
    }

    private fun runAction(block: suspend () -> Unit) {
        if (actionJob?.isActive == true) return
        actionJob = viewModelScope.launch {
            try { block() } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reportException(failure)
                updateSelection { it.copy(actionMessage = R.string.recap_action_error) }
            }
        }
    }

    fun share(font: Typeface) {
        if (shareJob?.isActive == true) return
        val data = currentData() ?: return
        val selection = state.value.selection
        val chapter = if (selection.month == null && selection.story) selection.chapter else RecapChapter.SUMMARY
        val request = ++shareRequest
        updateSelection { it.copy(sharing = true, actionMessage = null) }
        shareJob = viewModelScope.launch {
            try {
                val uri = actions.share(data, chapter, font)
                if (request == shareRequest) eventChannel.send(RecapEvent.Share(uri, request))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                reportException(failure)
                if (request == shareRequest) updateSelection { it.copy(actionMessage = R.string.recap_share_error) }
            } finally {
                if (request == shareRequest) updateSelection { it.copy(sharing = false) }
            }
        }
    }

    fun shareLaunchFailed() = updateSelection { it.copy(actionMessage = R.string.recap_share_error) }
    fun isCurrentShare(request: Long): Boolean = request == shareRequest
    fun dismissMessage() = updateSelection { it.copy(actionMessage = null) }

    private fun cancelShare() {
        shareRequest++
        shareJob?.cancel()
        updateSelection { it.copy(sharing = false) }
    }

    private fun currentData(): RecapData? {
        val success = state.value as? YearInMusicUiState.Success ?: return null
        if (success.selection.story) return success.storyData
        return if (success.selection.month == null) success.data else (success.monthState as? RecapMonthState.Success)?.data
    }

    private inline fun updateSelection(transform: (RecapSelection) -> RecapSelection) {
        state.update { current ->
            val selection = transform(current.selection)
            when (current) {
                is YearInMusicUiState.Loading -> current.copy(selection = selection)
                is YearInMusicUiState.Success -> current.copy(selection = selection)
                is YearInMusicUiState.Empty -> current.copy(selection = selection)
                is YearInMusicUiState.Error -> current.copy(selection = selection)
            }
        }
    }
}
