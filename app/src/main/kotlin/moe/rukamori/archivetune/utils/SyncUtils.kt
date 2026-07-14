/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.constants.SelectedYtmPlaylistsKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.toMediaMetadata
import timber.log.Timber
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils
    @Inject
    constructor(
        private val database: MusicDatabase,
        @ApplicationContext private val context: Context,
    ) {
        private val syncScope = CoroutineScope(Dispatchers.IO)
        private val syncEnabled = MutableStateFlow(true)
        private val syncGeneration = AtomicLong(0L)

        private val syncMutex = Mutex()
        private val playlistSyncMutex = Mutex()
        private val dbWriteSemaphore = Semaphore(2)

        init {
            syncScope.launch {
                context.dataStore.data
                    .map { it[YtmSyncKey] ?: true }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        syncEnabled.value = enabled
                        if (!enabled) {
                            syncGeneration.incrementAndGet()
                        }
                    }
            }
        }

        suspend fun performFullSync(authoritative: Boolean = false) =
            withContext(Dispatchers.IO) {
                if (authoritative) {
                    syncGeneration.incrementAndGet()
                    syncMutex.lock()
                } else if (!syncMutex.tryLock()) {
                    Timber.d("Sync already in progress, skipping")
                    return@withContext
                }

                try {
                    if (!isLoggedIn()) {
                        Timber.w("Skipping full sync - user not logged in")
                        return@withContext
                    }
                    if (!isYtmSyncEnabled()) {
                        Timber.w("Skipping full sync - sync disabled")
                        return@withContext
                    }

                    supervisorScope {
                        syncLikedSongs(authoritative = authoritative)
                        syncLibrarySongs(authoritative = authoritative)

                        listOf(
                            async { syncLikedAlbums(authoritative = authoritative) },
                            async { syncArtistsSubscriptions(authoritative = authoritative) },
                        ).awaitAll()

                        syncSavedPlaylists(authoritative = authoritative)
                        if (!authoritative) {
                            syncAutoSyncPlaylists()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during full sync")
                } finally {
                    syncMutex.unlock()
                }
            }

        suspend fun cleanupDuplicatePlaylists() =
            withContext(Dispatchers.IO) {
                try {
                    val allPlaylists = database.playlistsByNameAsc().first()
                    val browseIdGroups =
                        allPlaylists
                            .filter { it.playlist.browseId != null }
                            .groupBy { it.playlist.browseId }

                    for ((browseId, playlists) in browseIdGroups) {
                        if (playlists.size > 1) {
                            Timber.w("Found ${playlists.size} duplicate playlists for browseId: $browseId")
                            val toKeep =
                                playlists.maxByOrNull { it.songCount }
                                    ?: playlists.first()

                            playlists.filter { it.id != toKeep.id }.forEach { duplicate ->
                                Timber.d("Removing duplicate playlist: ${duplicate.playlist.name} (${duplicate.id})")
                                database.clearPlaylist(duplicate.id)
                                database.delete(duplicate.playlist)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error cleaning up duplicate playlists")
                }
            }

        private fun preferredPlaylistCopy(
            current: Playlist,
            candidate: Playlist,
        ): Playlist {
            val bySongCount = candidate.songCount.compareTo(current.songCount)
            if (bySongCount != 0) {
                return if (bySongCount > 0) candidate else current
            }

            val byLastUpdated = compareNullableDateTimes(candidate.playlist.lastUpdateTime, current.playlist.lastUpdateTime)
            if (byLastUpdated != 0) {
                return if (byLastUpdated > 0) candidate else current
            }

            val byCreatedAt = compareNullableDateTimes(candidate.playlist.createdAt, current.playlist.createdAt)
            if (byCreatedAt != 0) {
                return if (byCreatedAt > 0) candidate else current
            }

            if (candidate.playlist.isEditable != current.playlist.isEditable) {
                return if (candidate.playlist.isEditable) candidate else current
            }

            return if (candidate.id < current.id) candidate else current
        }

        private fun compareNullableDateTimes(
            first: LocalDateTime?,
            second: LocalDateTime?,
        ): Int {
            if (first == second) return 0
            if (first == null) return -1
            if (second == null) return 1
            return first.compareTo(second)
        }

        private fun canonicalPlaylistCopies(playlists: List<Playlist>): List<Playlist> =
            playlists
                .groupBy { it.playlist.browseId ?: it.id }
                .values
                .map { copies ->
                    copies.reduce(::preferredPlaylistCopy)
                }

        private suspend fun isLoggedIn(): Boolean {
            val cookie =
                context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .first()
            return hasYouTubeLoginCookie(cookie)
        }

        private suspend fun isYtmSyncEnabled(): Boolean {
            val enabled =
                context.dataStore.data
                    .map { it[YtmSyncKey] ?: true }
                    .first()
            syncEnabled.value = enabled
            if (!enabled) {
                syncGeneration.incrementAndGet()
            }
            return enabled
        }

        private fun isSyncStillEnabled(gen: Long): Boolean = syncEnabled.value && syncGeneration.get() == gen

        fun likeSong(s: SongEntity) {
            if (s.isLocal) return
            syncScope.launch {
                if (!isLoggedIn()) {
                    Timber.w("Skipping likeSong - user not logged in")
                    return@launch
                }
                if (!isYtmSyncEnabled()) {
                    Timber.w("Skipping likeSong - sync disabled")
                    return@launch
                }
                val gen = syncGeneration.get()
                if (!isSyncStillEnabled(gen)) return@launch
                YouTube.likeVideo(s.id, s.liked)
            }
        }

        fun likeSongs(songs: Collection<SongEntity>) {
            val uniqueSongs = songs.filterNot(SongEntity::isLocal).distinctBy { it.id }
            if (uniqueSongs.isEmpty()) return

            syncScope.launch {
                if (!isLoggedIn()) {
                    Timber.w("Skipping likeSongs - user not logged in")
                    return@launch
                }
                if (!isYtmSyncEnabled()) {
                    Timber.w("Skipping likeSongs - sync disabled")
                    return@launch
                }

                val gen = syncGeneration.get()
                uniqueSongs.chunked(8).forEach { batch ->
                    if (!isSyncStillEnabled(gen)) return@launch

                    coroutineScope {
                        batch
                            .map { song ->
                                async {
                                    if (!isSyncStillEnabled(gen)) return@async
                                    YouTube
                                        .likeVideo(song.id, song.liked)
                                        .onFailure { error ->
                                            Timber.w(error, "likeSongs: Failed to sync like for ${song.id}")
                                        }
                                }
                            }.awaitAll()
                    }
                }
            }
        }

        suspend fun syncLikedSongs(authoritative: Boolean = false) =
            coroutineScope {
                if (!isLoggedIn()) {
                    Timber.w("Skipping syncLikedSongs - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    Timber.w("Skipping syncLikedSongs - sync disabled")
                    return@coroutineScope
                }
                val gen = syncGeneration.get()
                YouTube
                    .playlist("LM")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val remoteSongs = page.songs.orEmpty()
                        if (remoteSongs.isEmpty() && !authoritative) {
                            Timber.w("syncLikedSongs: Remote playlist is empty")
                            return@onSuccess
                        }
                        val remoteIds = remoteSongs.map { it.id }.toSet()
                        if (authoritative) {
                            val localLikedSongs = database.likedSongsByNameAsc().first()
                            if (!isSyncStillEnabled(gen)) return@onSuccess
                            val staleLikedSongs =
                                localLikedSongs
                                    .asSequence()
                                    .map { it.song }
                                    .filterNot { it.isLocal }
                                    .filterNot { it.id in remoteIds }
                                    .map { it.copy(liked = false, likedDate = null) }
                                    .toList()
                            if (staleLikedSongs.isNotEmpty()) {
                                database.withTransaction {
                                    staleLikedSongs.forEach { update(it) }
                                }
                            }
                        }
                        val baseTimestamp = LocalDateTime.now()

                        remoteSongs.forEachIndexed { index, song ->
                            val timestamp = likedSongTimestamp(baseTimestamp, index)
                            launch {
                                if (!isSyncStillEnabled(gen)) return@launch
                                dbWriteSemaphore.withPermit {
                                    if (!isSyncStillEnabled(gen)) return@withPermit
                                    val dbSong = database.song(song.id).firstOrNull()
                                    database.withTransaction {
                                        if (!isSyncStillEnabled(gen)) return@withTransaction
                                        if (dbSong == null) {
                                            insert(song.toMediaMetadata()) { it.copy(liked = true, likedDate = timestamp) }
                                        } else if (!dbSong.song.liked || dbSong.song.likedDate == null) {
                                            update(dbSong.song.copy(liked = true, likedDate = timestamp))
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncLikedSongs: Failed to sync liked songs")
                    }
            }

        suspend fun syncLibrarySongs(authoritative: Boolean = false) =
            coroutineScope {
                if (!isLoggedIn()) {
                    Timber.w("Skipping syncLibrarySongs - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    Timber.w("Skipping syncLibrarySongs - sync disabled")
                    return@coroutineScope
                }
                val gen = syncGeneration.get()
                YouTube
                    .library("FEmusic_liked_videos")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val remoteSongs = page.items.filterIsInstance<SongItem>().reversed()
                        if (remoteSongs.isEmpty() && !authoritative) {
                            Timber.w("syncLibrarySongs: Remote library is empty")
                            return@onSuccess
                        }
                        val remoteIds = remoteSongs.map { it.id }.toSet()
                        val localSongs = database.songsByNameAsc().first()

                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val staleLibrarySongs =
                            localSongs
                                .asSequence()
                                .filter { !authoritative || !it.song.isLocal }
                                .filterNot { it.id in remoteIds }
                                .map { it.song.copy(inLibrary = null) }
                                .toList()
                        if (staleLibrarySongs.isNotEmpty()) {
                            database.withTransaction {
                                staleLibrarySongs.forEach { update(it) }
                            }
                        }

                        remoteSongs.forEach { song ->
                            launch {
                                if (!isSyncStillEnabled(gen)) return@launch
                                dbWriteSemaphore.withPermit {
                                    if (!isSyncStillEnabled(gen)) return@withPermit
                                    val dbSong = database.song(song.id).firstOrNull()
                                    database.withTransaction {
                                        if (!isSyncStillEnabled(gen)) return@withTransaction
                                        if (dbSong == null) {
                                            insert(song.toMediaMetadata()) { it.toggleLibrary() }
                                        } else if (dbSong.song.inLibrary == null) {
                                            update(dbSong.song.toggleLibrary())
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncLibrarySongs: Failed to sync library songs")
                    }
            }

        suspend fun syncLikedAlbums(authoritative: Boolean = false) =
            coroutineScope {
                if (!isLoggedIn()) {
                    Timber.w("Skipping syncLikedAlbums - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    Timber.w("Skipping syncLikedAlbums - sync disabled")
                    return@coroutineScope
                }
                val gen = syncGeneration.get()
                YouTube
                    .library("FEmusic_liked_albums")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val remoteAlbums = page.items.filterIsInstance<AlbumItem>().reversed()
                        if (remoteAlbums.isEmpty() && !authoritative) {
                            Timber.w("syncLikedAlbums: No liked albums found")
                            return@onSuccess
                        }
                        val remoteIds = remoteAlbums.map { it.id }.toSet()
                        val localAlbums = database.albumsLikedByNameAsc().first()

                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val staleAlbums =
                            localAlbums
                                .asSequence()
                                .filter { !authoritative || !it.album.isLocal }
                                .filterNot { it.id in remoteIds }
                                .map { it.album.localToggleLike() }
                                .toList()
                        if (staleAlbums.isNotEmpty()) {
                            database.withTransaction {
                                staleAlbums.forEach { update(it) }
                            }
                        }

                        remoteAlbums.forEach { album ->
                            launch {
                                if (!isSyncStillEnabled(gen)) return@launch
                                dbWriteSemaphore.withPermit {
                                    if (!isSyncStillEnabled(gen)) return@withPermit
                                    val dbAlbum = database.album(album.id).firstOrNull()
                                    YouTube
                                        .album(album.browseId)
                                        .onSuccess { albumPage ->
                                            if (!isSyncStillEnabled(gen)) return@onSuccess
                                            if (dbAlbum == null) {
                                                try {
                                                    database.insert(albumPage)
                                                    database.album(album.id).firstOrNull()?.let { newDbAlbum ->
                                                        database.update(newDbAlbum.album.localToggleLike())
                                                    }
                                                } catch (e: Exception) {
                                                    Timber.w("syncLikedAlbums: Failed to insert album ${album.id}", e)
                                                }
                                            } else if (dbAlbum.album.bookmarkedAt == null) {
                                                database.update(dbAlbum.album.localToggleLike())
                                            }
                                        }.onFailure { e ->
                                            Timber.w("syncLikedAlbums: Failed to fetch album ${album.id}", e)
                                        }
                                }
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncLikedAlbums: Failed to sync liked albums")
                    }
            }

        suspend fun syncArtistsSubscriptions(authoritative: Boolean = false) =
            coroutineScope {
                if (!isLoggedIn()) {
                    Timber.w("Skipping syncArtistsSubscriptions - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    Timber.w("Skipping syncArtistsSubscriptions - sync disabled")
                    return@coroutineScope
                }
                val gen = syncGeneration.get()
                YouTube
                    .library("FEmusic_library_corpus_artists")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val remoteArtists = page.items.filterIsInstance<ArtistItem>().distinctBy { it.id }
                        if (remoteArtists.isEmpty() && !authoritative) {
                            Timber.w("syncArtistsSubscriptions: No artist subscriptions found")
                            return@onSuccess
                        }
                        val now = LocalDateTime.now()
                        val remoteIds = remoteArtists.map { it.id }.toSet()
                        val localArtists = database.artistsBookmarkedByNameAsc().first()

                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val staleArtists =
                            localArtists
                                .asSequence()
                                .filter { !authoritative || !it.artist.isLocal }
                                .filterNot { it.id in remoteIds }
                                .map { it.artist.copy(bookmarkedAt = null, lastUpdateTime = now) }
                                .toList()
                        if (staleArtists.isNotEmpty()) {
                            database.withTransaction {
                                staleArtists.forEach { update(it) }
                            }
                        }

                        remoteArtists.forEachIndexed { index, artist ->
                            launch {
                                if (!isSyncStillEnabled(gen)) return@launch
                                dbWriteSemaphore.withPermit {
                                    if (!isSyncStillEnabled(gen)) return@withPermit
                                    val dbArtist = database.artist(artist.id).firstOrNull()
                                    val bookmarkedAt = now.minusSeconds(index.toLong())
                                    database.withTransaction {
                                        if (!isSyncStillEnabled(gen)) return@withTransaction
                                        if (dbArtist == null) {
                                            insert(
                                                ArtistEntity(
                                                    id = artist.id,
                                                    name = artist.title,
                                                    thumbnailUrl = artist.thumbnail,
                                                    channelId = artist.channelId,
                                                    bookmarkedAt = bookmarkedAt,
                                                ),
                                            )
                                        } else {
                                            val existing = dbArtist.artist
                                            val syncedThumbnail = artist.thumbnail ?: existing.thumbnailUrl
                                            val syncedChannelId = artist.channelId ?: existing.channelId
                                            val metadataChanged =
                                                existing.name != artist.title ||
                                                    existing.thumbnailUrl != syncedThumbnail ||
                                                    existing.channelId != syncedChannelId
                                            val needsBookmark = existing.bookmarkedAt == null

                                            if (metadataChanged || needsBookmark) {
                                                update(
                                                    existing.copy(
                                                        name = artist.title,
                                                        thumbnailUrl = syncedThumbnail,
                                                        channelId = syncedChannelId,
                                                        lastUpdateTime = now,
                                                        bookmarkedAt = existing.bookmarkedAt ?: bookmarkedAt,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncArtistsSubscriptions: Failed to sync artist subscriptions")
                    }
            }

        suspend fun syncSavedPlaylists(authoritative: Boolean = false) =
            playlistSyncMutex.withLock {
                if (!isLoggedIn()) {
                    Timber.w("Skipping syncSavedPlaylists - user not logged in")
                    return@withLock
                }
                if (!isYtmSyncEnabled()) {
                    Timber.w("Skipping syncSavedPlaylists - sync disabled")
                    return@withLock
                }

                cleanupDuplicatePlaylists()

                val gen = syncGeneration.get()

                YouTube
                    .library("FEmusic_liked_playlists")
                    .completed()
                    .onSuccess { page ->
                        if (!isSyncStillEnabled(gen)) return@onSuccess
                        val remotePlaylists =
                            page.items
                                .filterIsInstance<PlaylistItem>()
                                .filterNot { it.id == "LM" || it.id == "SE" }
                                .reversed()

                        if (remotePlaylists.isEmpty() && !authoritative) {
                            Timber.w("syncSavedPlaylists: No playlists found")
                            return@onSuccess
                        }

                        val selectedCsv = context.dataStore.data.first()[SelectedYtmPlaylistsKey] ?: ""
                        val selectedIds =
                            selectedCsv
                                .split(',')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet()

                        val localPlaylists = database.playlistsByNameAsc().first()
                        if (!isSyncStillEnabled(gen)) return@onSuccess

                        val now = LocalDateTime.now()
                        val remoteLikedIds = remotePlaylists.map { it.id }.toSet()

                        val stalePlaylists =
                            localPlaylists
                                .asSequence()
                                .map { it.playlist }
                                .filter { it.browseId != null }
                                .filter { it.browseId !in remoteLikedIds }
                                .map { it.copy(bookmarkedAt = null, lastUpdateTime = now) }
                                .toList()
                        if (stalePlaylists.isNotEmpty()) {
                            database.withTransaction {
                                stalePlaylists.forEach { update(it) }
                            }
                        }

                        val localPlaylistIdByBrowseId = HashMap<String, String>(remotePlaylists.size)
                        for (playlist in remotePlaylists) {
                            if (!isSyncStillEnabled(gen)) return@onSuccess
                            try {
                                val existingPlaylist = database.playlistByBrowseId(playlist.id).firstOrNull()
                                if (existingPlaylist == null) {
                                    val playlistEntity =
                                        PlaylistEntity(
                                            name = playlist.title,
                                            browseId = playlist.id,
                                            thumbnailUrl = playlist.thumbnail,
                                            isEditable = playlist.isEditable,
                                            bookmarkedAt = now,
                                            remoteSongCount =
                                                playlist.songCountText?.let {
                                                    Regex(
                                                        """\d+""",
                                                    ).find(it)?.value?.toIntOrNull()
                                                },
                                            playEndpointParams = playlist.playEndpoint?.params,
                                            shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                            radioEndpointParams = playlist.radioEndpoint?.params,
                                        )
                                    database.insert(playlistEntity)
                                    localPlaylistIdByBrowseId[playlist.id] = playlistEntity.id
                                    Timber.d("syncSavedPlaylists: Created new playlist ${playlist.title} (${playlist.id})")
                                } else {
                                    val baseEntity = existingPlaylist.playlist
                                    val likedEntity =
                                        if (baseEntity.bookmarkedAt == null) {
                                            baseEntity.copy(bookmarkedAt = now, lastUpdateTime = now)
                                        } else {
                                            baseEntity
                                        }
                                    database.update(likedEntity, playlist)
                                    localPlaylistIdByBrowseId[playlist.id] = likedEntity.id
                                    Timber.d("syncSavedPlaylists: Updated existing playlist ${playlist.title} (${playlist.id})")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "syncSavedPlaylists: Failed to upsert playlist ${playlist.title}")
                            }
                        }

                        val playlistsToSync =
                            if (selectedIds.isNotEmpty()) remotePlaylists.filter { it.id in selectedIds } else remotePlaylists
                        if (selectedIds.isNotEmpty() && playlistsToSync.isEmpty()) {
                            Timber.w(
                                "syncSavedPlaylists: Selected playlists not found in remote library; skipping song sync (selected=${selectedIds.size}, remote=${remotePlaylists.size})",
                            )
                        }

                        for (playlist in playlistsToSync) {
                            if (!isSyncStillEnabled(gen)) return@onSuccess
                            try {
                                val playlistId =
                                    localPlaylistIdByBrowseId[playlist.id]
                                        ?: database
                                            .playlistByBrowseId(playlist.id)
                                            .firstOrNull()
                                            ?.playlist
                                            ?.id
                                        ?: continue
                                syncPlaylist(
                                    browseId = playlist.id,
                                    playlistId = playlistId,
                                    authoritative = authoritative,
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to sync playlist ${playlist.title}")
                            }
                        }
                    }.onFailure { e ->
                        Timber.e(e, "syncSavedPlaylists: Failed to fetch playlists from YouTube")
                    }
            }

        suspend fun syncAutoSyncPlaylists() =
            coroutineScope {
                if (!isLoggedIn()) {
                    Timber.w("Skipping syncAutoSyncPlaylists - user not logged in")
                    return@coroutineScope
                }
                if (!isYtmSyncEnabled()) {
                    Timber.w("Skipping syncAutoSyncPlaylists - sync disabled")
                    return@coroutineScope
                }

                cleanupDuplicatePlaylists()

                val gen = syncGeneration.get()
                val autoSyncPlaylists =
                    try {
                        canonicalPlaylistCopies(
                            database
                                .playlistsByNameAsc()
                                .first()
                                .filter { it.playlist.isAutoSync && it.playlist.browseId != null },
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "syncAutoSyncPlaylists: Failed to fetch auto-sync playlists")
                        return@coroutineScope
                    }

                Timber.d("syncAutoSyncPlaylists: Found ${autoSyncPlaylists.size} playlists to sync")

                autoSyncPlaylists.forEach { playlist ->
                    launch {
                        if (!isSyncStillEnabled(gen)) return@launch
                        try {
                            dbWriteSemaphore.withPermit {
                                if (!isSyncStillEnabled(gen)) return@withPermit
                                val browseId =
                                    playlist.playlist.browseId ?: run {
                                        Timber.w("syncAutoSyncPlaylists: browseId is null for playlist ${playlist.playlist.name}")
                                        return@withPermit
                                    }
                                syncPlaylist(browseId, playlist.playlist.id)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to sync playlist ${playlist.playlist.name}")
                        }
                    }
                }
            }

        suspend fun syncPlaylistNow(
            browseId: String,
            playlistId: String,
            propagateFailures: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = playlistSyncMutex.withLock {
            syncPlaylist(
                browseId = browseId,
                playlistId = playlistId,
                propagateFailures = propagateFailures,
                onProgress = onProgress,
            )
        }

        private suspend fun syncPlaylist(
            browseId: String,
            playlistId: String,
            authoritative: Boolean = false,
            propagateFailures: Boolean = false,
            onProgress: (completedSongs: Int, totalSongs: Int) -> Unit = { _, _ -> },
        ) = coroutineScope {
            if (!isYtmSyncEnabled()) {
                Timber.w("syncPlaylist: Skipping - sync disabled")
                return@coroutineScope
            }
            val gen = syncGeneration.get()
            if (!isSyncStillEnabled(gen)) return@coroutineScope
            Timber.d("syncPlaylist: Starting sync for browseId=$browseId, playlistId=$playlistId")

            val page =
                YouTube.playlist(browseId).completed().getOrElse { e ->
                    Timber.e(e, "syncPlaylist: Failed to fetch playlist from YouTube")
                    if (propagateFailures) {
                        throw e
                    }
                    return@coroutineScope
                }
            if (!isSyncStillEnabled(gen)) return@coroutineScope
            val songs =
                page.songs
                    .orEmpty()
                    .filter { song -> song.setVideoId?.isNotBlank() == true }
                    .map(SongItem::toMediaMetadata)
            Timber.d("syncPlaylist: Fetched ${songs.size} songs from remote")

            if (songs.isEmpty()) {
                if (authoritative) {
                    database.withTransaction {
                        if (!isSyncStillEnabled(gen)) return@withTransaction
                        database.clearPlaylist(playlistId)
                    }
                }
                Timber.w("syncPlaylist: Remote playlist is empty, skipping sync")
                return@coroutineScope
            }

            val remoteIds = songs.mapNotNull { it.id }
            if (remoteIds.isEmpty()) {
                Timber.w("syncPlaylist: No valid song IDs found, skipping sync")
                return@coroutineScope
            }

            val localIds =
                try {
                    database
                        .playlistSongs(playlistId)
                        .first()
                        .sortedBy { it.map.position }
                        .map { it.song.id }
                } catch (e: Exception) {
                    Timber.w("syncPlaylist: Failed to fetch local songs", e)
                    emptyList()
                }

            if (remoteIds == localIds) {
                Timber.d("syncPlaylist: Local and remote are in sync, no changes needed")
                onProgress(remoteIds.size, remoteIds.size)
                return@coroutineScope
            }

            Timber.d("syncPlaylist: Updating local playlist (remote: ${remoteIds.size}, local: ${localIds.size})")

            try {
                onProgress(0, remoteIds.size)
                database.withTransaction {
                    if (!isSyncStillEnabled(gen)) return@withTransaction
                    database.clearPlaylist(playlistId)
                    var completedSongs = 0
                    songs.forEachIndexed { idx, song ->
                        if (!isSyncStillEnabled(gen)) return@withTransaction
                        val songId = song.id ?: return@forEachIndexed
                        if (database.song(songId).firstOrNull() == null) {
                            database.insert(song)
                        }
                        database.insert(
                            PlaylistSongMap(
                                songId = songId,
                                playlistId = playlistId,
                                position = idx,
                                setVideoId = song.setVideoId,
                            ),
                        )
                        completedSongs += 1
                        onProgress(completedSongs, remoteIds.size)
                    }
                }
                Timber.d("syncPlaylist: Successfully synced playlist")
            } catch (e: Exception) {
                Timber.e(e, "syncPlaylist: Error during database transaction")
                if (propagateFailures) {
                    throw e
                }
            }
        }
    }

internal fun likedSongTimestamp(
    baseTimestamp: LocalDateTime,
    index: Int,
): LocalDateTime = baseTimestamp.minusSeconds(index.toLong())
