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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.domain.player.EditQueueUseCase
import moe.rukamori.archivetune.domain.player.ObserveQueueUseCase
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.QueueRemoval
import moe.rukamori.archivetune.playback.QueueRepository
import moe.rukamori.archivetune.ui.player.QueueAction
import moe.rukamori.archivetune.ui.player.QueueChromeUi
import moe.rukamori.archivetune.ui.player.QueueEntries
import moe.rukamori.archivetune.ui.player.QueueEvent
import moe.rukamori.archivetune.ui.player.QueueScreenState
import moe.rukamori.archivetune.ui.player.QueueUiModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repository: QueueRepository,
    private val observeQueue: ObserveQueueUseCase,
    private val edit: EditQueueUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<QueueScreenState>(QueueScreenState.Loading)
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<QueueEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private val mutableChrome = MutableStateFlow(QueueChromeUi())
    val chrome = mutableChrome.asStateFlow()
    private var latest: QueueUiModel? = null
    private var selected = emptySet<String>()
    private var dragOrder: List<String>? = null
    private var draggedId: String? = null
    private var overflow = false
    private var playlist = false
    private var createPlaylist = false
    private var playlistName = ""
    private var playlistJob: Job? = null
    private val preferenceJobs = mutableMapOf<QueueAction, Job>()
    private var pendingRemoval: QueueRemoval? = null
    private var removalToken = 0L
    private var observationJob: Job? = null
    private var observationError: Int? = null

    init {
        viewModelScope.launch {
            edit.observeSleepTimer().catch { error ->
                Timber.e(error, "Failed to observe sleep timer")
                this@QueueViewModel.emit(QueueEvent.Message(R.string.queue_action_failed))
            }.collect { (enabled, remaining) ->
                mutableChrome.value = mutableChrome.value.copy(sleepTimerEnabled = enabled, sleepTimerTimeLeft = remaining)
            }
        }
        observe()
    }

    private fun observe() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            observeQueue().catch { error ->
                Timber.e(error, "Failed to observe queue")
                observationError = R.string.queue_action_failed
                publish()
            }.collect { model ->
                observationError = null
                latest = model
                selected = selected.intersect(model?.entries?.values.orEmpty().mapTo(mutableSetOf()) { it.id })
                if (model?.locked == true) { dragOrder = null; draggedId = null }
                publish()
            }
        }
    }

    fun bind(connection: PlayerConnection) = repository.attach(connection)
    fun unbind(connection: PlayerConnection) = repository.detach(connection)
    fun visible() = edit.refresh()

    fun sleepTimerClick() {
        if (mutableChrome.value.sleepTimerEnabled) edit.clearSleepTimer()
        else mutableChrome.value = mutableChrome.value.copy(showSleepTimer = true)
    }
    fun dismissSleepTimer() { mutableChrome.value = mutableChrome.value.copy(showSleepTimer = false) }
    fun sleepTimerValue(value: Float) { mutableChrome.value = mutableChrome.value.copy(sleepTimerValue = value) }
    fun startSleepTimer(minutes: Int) { edit.startSleepTimer(minutes); dismissSleepTimer() }

    private fun publish() {
        val model = latest
        if (model == null || model.entries.values.isEmpty()) {
            mutableState.value = observationError?.let { QueueScreenState.Error(it) } ?: QueueScreenState.Empty
            return
        }
        val rows = model.entries.values.associateBy { it.id }
        val order = dragOrder?.let { it.filter(rows::containsKey) + rows.keys.filterNot(it::contains) }
        val entries = (order?.mapNotNull(rows::get) ?: model.entries.values).map { it.copy(selected = it.id in selected) }
        val updated = model.copy(
            entries = QueueEntries(entries), selectionCount = selected.size, overflowOpen = overflow,
            playlistDialog = playlist, createPlaylistDialog = createPlaylist, savingPlaylist = playlistJob?.isActive == true,
            playlistName = playlistName,
        )
        mutableState.value = observationError?.let { QueueScreenState.Error(it, updated) } ?: QueueScreenState.Success(updated)
    }

    private fun emit(event: QueueEvent) { viewModelScope.launch { eventChannel.send(event) } }
    private fun failure(error: Exception) {
        Timber.e(error, "Queue action failed")
        emit(QueueEvent.Message(R.string.queue_action_failed))
    }

    fun onAction(action: QueueAction) {
        if (action == QueueAction.Retry && mutableState.value is QueueScreenState.Error) {
            observe()
            return
        }
        val model = latest ?: return
        when (action) {
            is QueueAction.Play -> {
                if (selected.isNotEmpty()) select(action.id)
                else if (!edit.play(action.id)) emit(QueueEvent.Message(R.string.not_allowed))
            }
            is QueueAction.Select -> select(action.id)
            is QueueAction.Menu -> edit.metadata(action.id)?.let {
                emit(QueueEvent.Menu(action.id, it, !model.guest && edit.canPlayNext(action.id)))
            }
            is QueueAction.PlayNext -> if (!model.guest) edit.playNext(action.id)
            is QueueAction.Remove -> if (!model.guest) remove(setOf(action.id))
            is QueueAction.SelectChip -> if (!model.guest) edit.chip(action.id)
            QueueAction.ToggleShuffle -> if (model.canControlPlayback) edit.shuffle()
            QueueAction.ToggleRepeat -> if (model.canControlPlayback) edit.repeat()
            QueueAction.ToggleLike -> edit.like()
            QueueAction.ToggleLock -> preference(QueueAction.ToggleLock) { if (!model.guest) edit.lock(!model.locked) }
            QueueAction.ToggleAutoplay -> preference(QueueAction.ToggleAutoplay) { if (!model.guest && model.autoplayAvailable) edit.autoplay(!model.autoplay) }
            QueueAction.ToggleOverflow -> { overflow = !overflow; publish() }
            QueueAction.Clear -> {
                overflow = false
                if (!model.guest) {
                    edit.clearContinuation()
                    remove(model.entries.values.filterNot { it.current }.mapTo(mutableSetOf()) { it.id })
                    preference(QueueAction.ToggleAutoplay, replace = true) { edit.autoplay(false) }
                }
            }
            QueueAction.ClearSelection -> { selected = emptySet(); publish() }
            QueueAction.SelectAll -> {
                selected = if (selected.size == model.entries.values.size) emptySet() else model.entries.values.mapTo(mutableSetOf()) { it.id }
                publish()
            }
            QueueAction.RemoveSelection -> if (!model.guest) remove(selected)
            QueueAction.AddToPlaylist -> { playlist = true; overflow = false; publish() }
            QueueAction.NewPlaylist -> { createPlaylist = true; playlistName = model.title.orEmpty(); overflow = false; publish() }
            QueueAction.DismissPlaylist -> { playlist = false; createPlaylist = false; publish() }
            QueueAction.PlaylistAdded -> { playlist = false; selected = emptySet(); publish() }
            is QueueAction.CreatePlaylist -> savePlaylist(action.name)
            is QueueAction.PlaylistName -> { playlistName = action.value; publish() }
            is QueueAction.Drag -> {
                if (model.locked) return
                val order = (dragOrder ?: model.entries.values.map { it.id }).toMutableList()
                val source = order.indexOf(action.from)
                val target = order.indexOf(action.to)
                if (source < 0 || target < 0) return
                order.add(target, order.removeAt(source))
                draggedId = action.from
                dragOrder = order
                publish()
            }
            QueueAction.FinishDrag -> {
                val id = draggedId
                val order = dragOrder
                if (!model.locked && id != null && order != null) {
                    val index = order.indexOf(id)
                    edit.move(id, order.getOrNull(index - 1))
                }
                dragOrder = null
                draggedId = null
                publish()
            }
            QueueAction.Retry -> edit.refresh(force = true)
        }
    }

    private fun preference(action: QueueAction, replace: Boolean = false, block: suspend () -> Unit) {
        if (replace) preferenceJobs[action]?.cancel()
        else if (preferenceJobs[action]?.isActive == true) return
        preferenceJobs[action] = viewModelScope.launch {
            try { block() } catch (error: CancellationException) { throw error } catch (error: Exception) { failure(error) }
        }
    }

    private fun select(id: String) {
        selected = if (id in selected) selected - id else selected + id
        publish()
    }

    private fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        val removal = edit.remove(ids) ?: return
        pendingRemoval?.let(edit::discard)
        pendingRemoval = removal
        selected = selected - ids
        emit(QueueEvent.Removed(removal.removedItems.size, removal.removedItems.firstOrNull()?.mediaMetadata?.title?.toString(), ++removalToken))
        publish()
    }

    fun finishRemoval(token: Long, undo: Boolean) {
        if (token != removalToken) return
        val removal = pendingRemoval ?: return
        pendingRemoval = null
        if (undo && !edit.undo(removal)) emit(QueueEvent.Message(R.string.queue_undo_unavailable))
        edit.discard(removal)
    }

    suspend fun prepareSelectedSongs(): List<String> = try {
        edit.prepareSongs(selected)
    } catch (error: CancellationException) { throw error } catch (error: Exception) { failure(error); emptyList() }

    fun playlistAdded(count: Int, names: List<String>) {
        onAction(QueueAction.PlaylistAdded)
        val event = when {
            count == 1 && names.size == 1 -> QueueEvent.Message(R.string.added_to_playlist, listOf(names.first()))
            names.size == 1 -> QueueEvent.Message(R.string.added_n_songs_to_playlist, listOf(count, names.first()))
            count == 1 -> QueueEvent.Message(R.string.added_to_n_playlists, listOf(names.size))
            else -> QueueEvent.Message(R.string.added_n_songs_to_n_playlists, listOf(count, names.size))
        }
        emit(event)
    }

    private fun savePlaylist(name: String) {
        if (playlistJob?.isActive == true || name.isBlank() || selected.isEmpty()) return
        val ids = selected.toSet()
        playlistJob = viewModelScope.launch {
            try {
                val count = edit.createPlaylist(name, ids)
                if (count > 0) {
                    createPlaylist = false
                    selected = emptySet()
                    emit(QueueEvent.Message(R.string.added_to_playlist, listOf(name.trim())))
                }
            } catch (error: CancellationException) { throw error } catch (error: Exception) { failure(error) }
            finally { playlistJob = null; publish() }
        }
        publish()
    }

    override fun onCleared() {
        pendingRemoval?.let(edit::discard)
        super.onCleared()
    }
}
