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
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.extensions.filterExplicit
import moe.rukamori.archivetune.extensions.filterVideo
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
    ) : ViewModel() {
        private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
        val cachedSongs: StateFlow<List<Song>> = _cachedSongs

        init {
            viewModelScope.launch(Dispatchers.IO) {
                while (true) {
                    val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                    val hideVideo = context.dataStore.get(HideVideoKey, false)
                    val pureCacheIds = playerCache.keys.toSet().subtract(downloadCache.keys.toSet())

                    val songs =
                        pureCacheIds
                            .chunked(SONG_QUERY_BATCH_SIZE)
                            .flatMap { ids -> database.getSongsByIds(ids) }

                    val completeSongs = songs.filter { it.isFullyCached() }

                    val cachedAt = LocalDateTime.now()
                    val undatedSongs = completeSongs.filter { it.song.dateDownload == null }
                    if (undatedSongs.isNotEmpty()) {
                        database.query {
                            undatedSongs.forEach { update(it.song.copy(dateDownload = cachedAt)) }
                        }
                    }

                    _cachedSongs.value =
                        completeSongs
                            .map { if (it.song.dateDownload == null) it.copy(song = it.song.copy(dateDownload = cachedAt)) else it }
                            .sortedByDescending { it.song.dateDownload }
                            .filterExplicit(hideExplicit)
                            .filterVideo(hideVideo)

                    delay(1000)
                }
            }
        }

        fun removeSongFromCache(songId: String) {
            playerCache.removeResource(songId)
        }

        private fun Song.isFullyCached(): Boolean {
            val contentLength =
                ContentMetadata
                    .getContentLength(playerCache.getContentMetadata(id))
                    .takeIf { it > 0L }
                    ?: format?.contentLength?.takeIf { it > 0L }
                    ?: return false
            return playerCache.isCached(id, 0L, contentLength)
        }

        private companion object {
            const val SONG_QUERY_BATCH_SIZE = 500
        }
    }
