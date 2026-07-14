/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.downloads

import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.playback.DownloadUtil
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadRepositorySnapshot(
    val songs: List<Song>,
    val albums: List<Album>,
    val playlists: List<Playlist>,
    val playlistSongMaps: List<PlaylistSongMap>,
    val downloads: Map<String, Download>,
)

interface DownloadRepository {
    fun observeDownloads(): Flow<DownloadRepositorySnapshot>

    fun pause(songIds: Collection<String>)

    fun resume(songIds: Collection<String>)

    fun remove(songIds: Collection<String>)
}

@Singleton
class Media3DownloadRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
    ) : DownloadRepository {
        override fun observeDownloads(): Flow<DownloadRepositorySnapshot> =
            combine(
                database.allSongs(),
                database.allAlbumsForDownloads(),
                database.allPlaylistsForDownloads(),
                database.allPlaylistSongMapsForDownloads(),
            ) { songs, albums, playlists, playlistSongMaps ->
                DownloadRepositorySnapshot(
                    songs = songs,
                    albums = albums,
                    playlists = playlists,
                    playlistSongMaps = playlistSongMaps,
                    downloads = emptyMap(),
                )
            }.combine(downloadUtil.downloads) { snapshot, downloads ->
                snapshot.copy(downloads = downloads)
            }.flowOn(Dispatchers.IO)

        override fun pause(songIds: Collection<String>) {
            songIds.distinct().forEach { songId ->
                downloadUtil.downloadManager.setStopReason(songId, PAUSED_STOP_REASON)
            }
        }

        override fun resume(songIds: Collection<String>) {
            songIds.distinct().forEach { songId ->
                val download = downloadUtil.downloads.value[songId]
                if (download?.state == Download.STATE_FAILED) {
                    downloadUtil.downloadManager.addDownload(download.request)
                } else {
                    downloadUtil.downloadManager.setStopReason(songId, NO_STOP_REASON)
                }
            }
        }

        override fun remove(songIds: Collection<String>) {
            songIds.distinct().forEach(downloadUtil.downloadManager::removeDownload)
        }

        private companion object {
            const val NO_STOP_REASON = 0
            const val PAUSED_STOP_REASON = 1
        }
    }
