/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.compose.runtime.snapshotFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.QueueEditLockKey
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.getQueueWindows
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.isLocalMediaId
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

data class QueueEntryData(val id: String, val metadata: MediaMetadata, val current: Boolean, val recommended: Boolean)

data class QueuePlaybackData(
    val entries: List<QueueEntryData>, val playing: Boolean, val repeat: Int, val shuffle: Boolean,
)

data class QueueExtraData(
    val title: String?, val liked: Boolean, val recommendations: QueueRecommendations,
    val guest: Boolean, val loading: Boolean, val canControlPlayback: Boolean,
    val currentSongIsLocal: Boolean,
)

data class QueueSnapshot(
    val playback: QueuePlaybackData, val extra: QueueExtraData, val locked: Boolean, val autoplay: Boolean,
    val autoplayAvailable: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelScoped
class QueueRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val connection = MutableStateFlow<PlayerConnection?>(null)
    private val identities = mutableMapOf<Any, String>()

    fun attach(value: PlayerConnection) {
        if (connection.value !== value) identities.clear()
        connection.value = value
    }

    fun detach(value: PlayerConnection) {
        connection.compareAndSet(value, null)
    }

    fun observeSleepTimer(): Flow<Pair<Boolean, Long>> = connection.flatMapLatest { connection ->
        if (connection == null) flowOf(false to 0L) else {
            val timer = connection.service.sleepTimer
            snapshotFlow { timer.triggerTime to timer.pauseWhenSongEnd }.flatMapLatest { (trigger, endOfSong) ->
                if (trigger == -1L && !endOfSong) flowOf(false to 0L) else flow {
                    while (currentCoroutineContext().isActive) {
                        val remaining = if (endOfSong) connection.player.duration - connection.player.currentPosition
                            else trigger - System.currentTimeMillis()
                        emit(true to remaining.coerceAtLeast(0L))
                        delay(1000L)
                    }
                }
            }
        }
    }

    fun startSleepTimer(minutes: Int) { connection.value?.service?.sleepTimer?.start(minutes) }
    fun clearSleepTimer() { connection.value?.service?.sleepTimer?.clear() }

    private fun key(window: Timeline.Window): String =
        window.mediaItem.queueEntryId ?: identities.getOrPut(window.uid) { UUID.randomUUID().toString() }

    private fun windows(): List<Timeline.Window> = connection.value?.player?.getQueueWindows().orEmpty()
    private fun window(id: String): Timeline.Window? = windows().firstOrNull { key(it) == id }

    fun observe(): Flow<QueueSnapshot?> = connection.flatMapLatest { player ->
        if (player == null) flowOf(null) else {
            val playback = combine(
                player.queueWindows, player.currentWindowIndex, player.isPlaying, player.repeatMode, player.shuffleModeEnabled,
            ) { windows, current, playing, repeat, shuffle ->
                val liveUids = windows.mapTo(mutableSetOf()) { it.uid }
                identities.keys.retainAll(liveUids)
                QueuePlaybackData(
                    windows.mapIndexedNotNull { index, window ->
                        window.mediaItem.metadata?.let {
                            QueueEntryData(key(window), it, index == current, window.mediaItem.isQueueRecommendation)
                        }
                    }, playing, repeat, shuffle,
                )
            }
            val extra = combine(
                player.queueTitle, player.currentSong, player.service.queueRecommendations,
                player.service.togetherSessionState, player.service.infiniteQueueLoading,
            ) { title, song, recommendations, together, loading ->
                QueueExtraData(title, song?.song?.liked == true, recommendations,
                    (together as? TogetherSessionState.Joined)?.role is TogetherRole.Guest, loading,
                    together !is TogetherSessionState.Joined || together.role !is TogetherRole.Guest || together.roomState.settings.allowGuestsToControlPlayback,
                    currentSongIsLocal = song?.song?.isLocal == true)
            }
            combine(playback, extra, context.dataStore.data) { data, details, preferences ->
                val metadata = data.entries.firstOrNull { it.current }?.metadata
                QueueSnapshot(data, details, preferences[QueueEditLockKey] ?: true, preferences[AutoLoadMoreKey] ?: true,
                    metadata != null && !metadata.isPodcast && !metadata.id.isLocalMediaId() &&
                        !details.currentSongIsLocal)
            }
        }
    }

    fun refresh(force: Boolean = false) { connection.value?.service?.refreshQueueRecommendations(force) }
    fun chip(id: String) { connection.value?.service?.selectQueueChip(id) }
    fun metadata(id: String): MediaMetadata? = window(id)?.mediaItem?.metadata
    fun canPlayNext(id: String): Boolean {
        val list = windows()
        val current = connection.value?.player?.currentMediaItemIndex
        val index = list.indexOfFirst { it.firstPeriodIndex == current }
        return list.getOrNull(index)?.let(::key) != id && list.getOrNull(index + 1)?.let(::key) != id
    }

    fun play(id: String): Boolean {
        val connection = connection.value ?: return false
        val window = window(id) ?: return false
        val joined = connection.service.togetherSessionState.value as? TogetherSessionState.Joined
        if (joined?.role is TogetherRole.Guest) {
            if (!joined.roomState.settings.allowGuestsToControlPlayback) return false
            if (window.firstPeriodIndex == connection.player.currentMediaItemIndex) connection.player.togglePlayPause()
            else connection.service.requestTogetherControl(ControlAction.SeekToTrack(window.mediaItem.mediaId, 0L))
        } else if (window.firstPeriodIndex == connection.player.currentMediaItemIndex) {
            connection.player.togglePlayPause()
        } else {
            connection.player.seekToDefaultPosition(window.firstPeriodIndex)
            connection.player.playWhenReady = true
        }
        return true
    }

    fun playNext(id: String) { window(id)?.let { connection.value?.moveQueueItemToNext(it.firstPeriodIndex) } }
    fun shuffle() { connection.value?.player?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { connection.value?.player?.toggleRepeatMode() }
    fun like() { connection.value?.service?.toggleLike() }
    fun clearContinuation() { connection.value?.service?.clearQueueContinuation() }
    suspend fun lock(value: Boolean) { context.dataStore.edit { it[QueueEditLockKey] = value } }
    suspend fun autoplay(value: Boolean) {
        context.dataStore.edit { it[AutoLoadMoreKey] = value }
        connection.value?.service?.let { if (value) { it.onInfiniteQueueEnabled(); it.refreshQueueRecommendations() } else it.onInfiniteQueueDisabled() }
    }

    internal fun remove(ids: Set<String>): QueueRemoval? =
        connection.value?.removeQueueItems(windows().filter { key(it) in ids })
    internal fun undo(removal: QueueRemoval): Boolean = connection.value?.restoreQueueItems(removal) == true
    internal fun discard(removal: QueueRemoval) { connection.value?.discardQueueRemoval(removal) }

    fun move(id: String, after: String?) {
        val connection = connection.value ?: return
        val list = windows()
        val source = list.indexOfFirst { key(it) == id }
        if (source < 0) return
        val reordered = list.toMutableList()
        val item = reordered.removeAt(source)
        val anchor = if (after == null) -1 else reordered.indexOfFirst { key(it) == after }
        if (after != null && anchor < 0) return
        val target = anchor + 1
        reordered.add(target, item)
        if (connection.player.shuffleModeEnabled) {
            connection.localPlayer.setShuffleOrder(DefaultShuffleOrder(reordered.map { it.firstPeriodIndex }.toIntArray(), System.currentTimeMillis()))
        } else connection.player.moveMediaItem(item.firstPeriodIndex, target)
    }

    suspend fun prepareSongs(ids: Set<String>): List<String> {
        val database = connection.value?.database ?: return emptyList()
        val songs = windows().filter { key(it) in ids }.mapNotNull { it.mediaItem.metadata }
        return withContext(Dispatchers.IO) {
            database.withTransaction { songs.forEach { insert(it) } }
            songs.map { it.id }
        }
    }

    suspend fun createPlaylist(name: String, ids: Set<String>): Int {
        val database = connection.value?.database ?: return 0
        val songs = windows().filter { key(it) in ids }.mapNotNull { it.mediaItem.metadata }
        if (songs.isEmpty()) return 0
        withContext(Dispatchers.IO) {
            val playlist = PlaylistEntity(name = name, bookmarkedAt = LocalDateTime.now(), isEditable = true)
            database.withTransaction {
                insert(playlist)
                songs.forEachIndexed { index, song ->
                    insert(song)
                    insert(PlaylistSongMap(playlistId = playlist.id, songId = song.id, position = index, setVideoId = song.setVideoId))
                }
            }
        }
        return songs.size
    }
}
