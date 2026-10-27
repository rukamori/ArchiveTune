/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.domain.player

import moe.rukamori.archivetune.playback.QueueRepository
import javax.inject.Inject

class EditQueueUseCase @Inject constructor(private val repository: QueueRepository) {
    fun play(id: String) = repository.play(id)
    fun playNext(id: String) = repository.playNext(id)
    fun shuffle() = repository.shuffle()
    fun repeat() = repository.repeat()
    fun like() = repository.like()
    fun clearContinuation() = repository.clearContinuation()
    fun chip(id: String) = repository.chip(id)
    fun refresh(force: Boolean = false) = repository.refresh(force)
    fun metadata(id: String) = repository.metadata(id)
    fun canPlayNext(id: String) = repository.canPlayNext(id)
    fun observeSleepTimer() = repository.observeSleepTimer()
    fun startSleepTimer(minutes: Int) = repository.startSleepTimer(minutes)
    fun clearSleepTimer() = repository.clearSleepTimer()
    suspend fun lock(value: Boolean) = repository.lock(value)
    suspend fun autoplay(value: Boolean) = repository.autoplay(value)
    fun move(id: String, after: String?) = repository.move(id, after)
    suspend fun prepareSongs(ids: Set<String>) = repository.prepareSongs(ids)
    suspend fun createPlaylist(name: String, ids: Set<String>): Int {
        val title = name.trim()
        require(title.isNotEmpty())
        return repository.createPlaylist(title, ids)
    }
    internal fun remove(ids: Set<String>) = repository.remove(ids)
    internal fun undo(removal: moe.rukamori.archivetune.playback.QueueRemoval) = repository.undo(removal)
    internal fun discard(removal: moe.rukamori.archivetune.playback.QueueRemoval) = repository.discard(removal)
}
