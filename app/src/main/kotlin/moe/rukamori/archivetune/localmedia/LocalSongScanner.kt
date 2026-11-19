/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.LocalMusicIdentity
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.AlbumArtistMap
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongAlbumMap
import moe.rukamori.archivetune.db.entities.SongArtistMap
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.lyrics.LyricsUtils
import timber.log.Timber

data class LocalSongScanConfig(
    val minimumDurationSeconds: Int = 0,
    val includedFolders: Set<String> = emptySet(),
    val excludedFolders: Set<String> = emptySet(),
    val autoScanEnabled: Boolean = true,
) {
    val sanitizedMinimumDurationSeconds: Int
        get() = minimumDurationSeconds.coerceAtLeast(0)

    val sanitizedIncludedFolders: Set<String>
        get() = deduplicateFolderEntries(includedFolders)

    val sanitizedExcludedFolders: Set<String>
        get() = deduplicateFolderEntries(excludedFolders)

    val requiresMediaPermission: Boolean
        get() = includedFolders.isEmpty() || includedFolders.any { LocalMusicFolder.treeUri(it) == null }

    companion object {
        private val DuplicateSlashRegex = Regex("/+")

        fun normalizeFolderEntry(raw: String): String =
            LocalMusicFolder.treeUri(raw.trim())?.toString() ?: raw
                .trim()
                .replace('\\', '/')
                .replace(DuplicateSlashRegex, "/")
                .trim('/')

        fun sameFolder(first: String, second: String): Boolean {
            val firstFolder = LocalMusicFolder.fromEntry(first)
            val secondFolder = LocalMusicFolder.fromEntry(second)
            return firstFolder.path.equals(secondFolder.path, ignoreCase = true) &&
                (firstFolder.volume == null || secondFolder.volume == null ||
                    firstFolder.volume.equals(secondFolder.volume, ignoreCase = true))
        }

        fun deduplicateFolderEntries(entries: Iterable<String>): Set<String> {
            val deduplicated = linkedMapOf<String, String>()
            entries.forEach { entry ->
                val normalized = normalizeFolderEntry(entry)
                if (normalized.isNotEmpty()) {
                    deduplicated.putIfAbsent(normalized.lowercase(Locale.ROOT), normalized)
                }
            }
            return deduplicated.values.toSet()
        }
    }
}

@Immutable
data class LocalSongScanSummary(
    val scannedSongs: Int,
    val removedSongs: Int,
    val metadataLookupFailed: Boolean = false,
)

@Singleton
class LocalSongScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val metadataReader: LocalAudioMetadataReader,
    private val metadataRepository: LocalMetadataRepository,
    private val mediaIndexer: LocalMediaIndexer,
    private val foldersRepository: LocalMusicFoldersRepository,
) {
    private val scanMutex = Mutex()

    suspend fun scanDevice(scanConfig: LocalSongScanConfig = LocalSongScanConfig()): LocalSongScanSummary =
        withContext(Dispatchers.IO) {
            scanMutex.withLock {
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else Manifest.permission.READ_EXTERNAL_STORAGE
                val canReadMediaStore = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                val needsDeviceScan = scanConfig.requiresMediaPermission
                if (!canReadMediaStore && needsDeviceScan) {
                    throw SecurityException("Audio library access has not been granted")
                }
                if (canReadMediaStore && needsDeviceScan) {
                    mediaIndexer.indexMissingAudio()
                }
                val scanned = queryTracks(scanConfig, canReadMediaStore)
                val ids = scanned.map(LocalTrackRecord::id)
                val enriched = enrichTracks(scanned, loadSongs(ids), loadFormats(ids))
                val summary = database.withTransaction {
                    val scannedIds = enriched.tracks.map(LocalTrackRecord::id)
                    val scannedIdSet = scannedIds.toSet()
                    val removedIds = localSongIds().filterNot(scannedIdSet::contains)
                    removedIds.chunked(SqlBatchSize).forEach(::deleteSongsByIds)
                    val existingSongs = loadSongs(scannedIds)
                    val existingLyrics = loadLyrics(scannedIds)
                    val existingFormats = loadFormats(scannedIds)
                    val resolver = CatalogResolver()
                    val artistCredits = enriched.tracks.flatMap { track ->
                        (track.artists + albumArtistNames(track)).distinct().map { name -> name to track }
                    }.groupBy { LocalMusicIdentity.normalize(it.first) }
                    val artistIds = artistCredits.mapValues { (_, credits) ->
                        currentCoroutineContext().ensureActive()
                        val name = credits.first().first
                        val matches = credits.map { it.second }.filter { it.matchedArtistName?.let { matched -> sameName(name, matched) } == true }
                        resolver.resolveArtist(
                            name,
                            matches.mapNotNull { it.remoteArtistId }.distinct().singleOrNull(),
                            matches.firstNotNullOfOrNull { it.artistThumbnailUrl },
                        )
                    }
                    val albumIds = mutableMapOf<String, String>()
                    enriched.tracks.filter { it.albumName != null }.groupBy(::albumKey).forEach { (key, tracks) ->
                        val albumArtistIds = albumArtistNames(tracks.first()).map { name ->
                            artistIds.getValue(LocalMusicIdentity.normalize(name))
                        }.distinct()
                        val previousIds = tracks.mapNotNull { existingSongs[it.id]?.song?.albumId }.toSet()
                        albumIds[key] = resolver.resolveAlbum(tracks, albumArtistIds, previousIds)
                    }
                    enriched.tracks.forEach { track ->
                        currentCoroutineContext().ensureActive()
                        val existing = existingSongs[track.id]?.song
                        val oldFormat = existingFormats[track.id]
                        val albumId = track.albumName?.let { albumIds[albumKey(track)] }
                        val base = existing ?: SongEntity(id = track.id, title = track.title, dateDownload = null)
                        upsert(base.copy(
                            title = if (base.titleOverride) base.title else track.title,
                            duration = track.durationSeconds,
                            thumbnailUrl = track.thumbnailUrl ?: albumId?.let { resolver.albumArtwork(it) } ?: track.mediaStoreThumbnailUrl,
                            albumId = albumId,
                            albumName = track.albumName,
                            albumArtist = track.albumArtist,
                            trackNumber = track.trackNumber,
                            discNumber = track.discNumber,
                            year = track.year,
                            dateModified = track.dateModified,
                            isLocal = true,
                        ))
                        val unchanged = isUnchanged(track, existing, oldFormat)
                        upsert(FormatEntity(
                            id = track.id,
                            itag = -1,
                            mimeType = track.mimeType,
                            codecs = if (unchanged) oldFormat!!.codecs else "",
                            bitrate = if (unchanged) oldFormat!!.bitrate else 0,
                            sampleRate = if (unchanged) oldFormat!!.sampleRate else null,
                            contentLength = track.sizeBytes,
                            loudnessDb = if (unchanged) oldFormat!!.loudnessDb else null,
                            perceptualLoudnessDb = if (unchanged) oldFormat!!.perceptualLoudnessDb else null,
                            playbackUrl = null,
                        ))
                        deleteSongArtistMaps(track.id)
                        track.artists.map { artistIds.getValue(LocalMusicIdentity.normalize(it)) }.distinct().forEachIndexed { index, artistId ->
                            insert(SongArtistMap(songId = track.id, artistId = artistId, position = index))
                        }
                        deleteSongAlbumMaps(track.id)
                        if (albumId != null) {
                            insert(SongAlbumMap(songId = track.id, albumId = albumId, index = track.trackNumber?.minus(1) ?: (Int.MAX_VALUE - 1)))
                        }
                        updateEmbeddedLyrics(track, existingLyrics[track.id])
                    }
                    refreshLocalAlbumCounts()
                    pruneLocalAlbums()
                    pruneLocalArtists()
                    pruneFormats()
                    prunePlayCounts()
                    LocalSongScanSummary(enriched.tracks.size, removedIds.size, enriched.lookupFailed)
                }
                val retained = database.localArtworkUrls().mapNotNull { Uri.parse(it).lastPathSegment }.toSet()
                pruneUnusedArtworkFiles(retained)
                summary
            }
        }

    private suspend fun enrichTracks(
        tracks: List<LocalTrackRecord>,
        previousSongs: Map<String, Song>,
        previousFormats: Map<String, FormatEntity>,
    ): EnrichedTracks {
        val unknownArtist = context.getString(R.string.unknown_artist)
        val plans = tracks.map { scanned ->
            currentCoroutineContext().ensureActive()
            val previous = previousSongs[scanned.id]
            val unchanged = isUnchanged(scanned, previous?.song, previousFormats[scanned.id])
            val track = if (unchanged && previous != null) {
                scanned.copy(
                    albumName = scanned.albumName ?: previous.song.albumName,
                    albumArtist = scanned.albumArtist ?: previous.song.albumArtist,
                    year = scanned.year ?: previous.song.year,
                    thumbnailUrl = scanned.thumbnailUrl ?: previous.song.thumbnailUrl?.takeUnless { it.startsWith("content://media/") },
                    trackNumber = scanned.trackNumber ?: previous.song.trackNumber,
                    discNumber = scanned.discNumber ?: previous.song.discNumber,
                    remoteAlbumId = previous.album?.takeUnless { it.isLocal }?.id,
                )
            } else scanned
            val lookupArtist = track.albumArtist?.let(::splitArtistNames)?.firstOrNull()
                ?: track.artists.firstOrNull { it != unknownArtist }
            val hasTrackArtist = track.artists.any { it != unknownArtist }
            val lookupAlbum = track.albumName != null && hasTrackArtist
            val query = LocalMetadataQuery(
                title = if (lookupAlbum) "" else track.title,
                artist = lookupArtist,
                albumArtist = track.albumArtist,
                album = track.albumName,
                durationSeconds = if (lookupAlbum) 0 else track.durationSeconds,
                year = track.year,
            )
            val needsLookup = !unchanged || track.albumArtist == null || track.year == null || track.thumbnailUrl == null || track.remoteAlbumId == null
            EnrichmentPlan(
                track = track,
                query = query,
                lookupArtist = lookupArtist,
                hasTrackArtist = hasTrackArtist,
                lookupEligible = lookupArtist != null && needsLookup,
            )
        }
        val lookupFailed = AtomicBoolean(false)
        val queries = plans.asSequence().filter(EnrichmentPlan::lookupEligible).map(EnrichmentPlan::query).distinct().toList()
        val matches = queries.mapConcurrently(MetadataLookupParallelism) { query ->
            if (lookupFailed.get()) {
                null
            } else try {
                metadataRepository.find(query)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (lookupFailed.compareAndSet(false, true)) {
                    Timber.tag(LogTag).w(error, "Optional local metadata lookup failed")
                }
                null
            }
        }
        val matchesByQuery = queries.zip(matches).toMap()
        val enriched = plans.map { plan ->
            currentCoroutineContext().ensureActive()
            var track = plan.track
            val lookupArtist = plan.lookupArtist
            val hasTrackArtist = plan.hasTrackArtist
            val match = matchesByQuery[plan.query]
            if (match != null) {
                track = track.copy(
                    artists = if (hasTrackArtist) track.artists else match.artist?.let(::splitArtistNames)?.takeIf { it.isNotEmpty() } ?: track.artists,
                    albumName = track.albumName ?: cleanTag(match.album),
                    albumArtist = track.albumArtist ?: cleanTag(match.albumArtist),
                    year = track.year ?: match.year,
                    thumbnailUrl = track.thumbnailUrl ?: match.thumbnailUrl,
                    trackNumber = track.trackNumber ?: match.trackNumber,
                    remoteAlbumId = match.albumId ?: track.remoteAlbumId,
                    remoteArtistId = match.artistId,
                    matchedArtistName = lookupArtist,
                    artistThumbnailUrl = match.artistThumbnailUrl,
                )
            }
            track
        }
        return EnrichedTracks(enriched, lookupFailed.get())
    }

    private suspend fun <T, R> List<T>.mapConcurrently(parallelism: Int, transform: suspend (T) -> R): List<R> {
        if (isEmpty()) return emptyList()
        return coroutineScope {
            val permits = Semaphore(parallelism)
            map { item ->
                async {
                    permits.withPermit {
                        currentCoroutineContext().ensureActive()
                        transform(item)
                    }
                }
            }.awaitAll()
        }
    }

    private inner class CatalogResolver {
        private val artists = database.catalogArtistEntities().associateByTo(linkedMapOf()) { it.id }
        private val albums = database.catalogAlbumEntities().associateByTo(linkedMapOf()) { it.id }
        private val albumArtists = database.catalogAlbumArtistMaps().groupBy { it.albumId }
            .mapValuesTo(mutableMapOf()) { (_, links) ->
                links.mapNotNull { artists[it.artistId]?.name?.let(LocalMusicIdentity::normalize) }.toSet()
            }

        fun albumArtwork(id: String): String? = albums[id]?.thumbnailUrl

        fun resolveArtist(name: String, remoteId: String?, thumbnailUrl: String?): String {
            val candidates = artists.values.filter { sameName(it.name, name) }
            val remote = candidates.filter { !it.isLocal && it.isYouTubeArtist }.singleOrNull()
            val id = remoteId ?: remote?.id ?: candidates.firstOrNull { it.isLocal }?.id
                ?: "LOCAL_ARTIST_${stableHash(LocalMusicIdentity.normalize(name))}"
            val existing = artists[id]
            val entity = existing?.copy(thumbnailUrl = existing.thumbnailUrl ?: thumbnailUrl)
                ?: ArtistEntity(id = id, name = name, thumbnailUrl = thumbnailUrl, isLocal = remoteId == null && remote == null)
            database.deleteLocalMusicAlias(id, "artist")
            database.upsert(entity)
            artists[id] = entity
            candidates.filter { it.isLocal && it.id != id }.forEach { obsolete ->
                database.mergeLocalArtist(obsolete.id, id)
                artists.remove(obsolete.id)
            }
            artists[id] = database.getArtistById(id) ?: entity
            return id
        }

        fun resolveAlbum(tracks: List<LocalTrackRecord>, artistIds: List<String>, previousIds: Set<String>): String {
            val first = tracks.first()
            val title = requireNotNull(first.albumName)
            val names = albumArtistNames(first).map(LocalMusicIdentity::normalize).toSet()
            val year = tracks.mapNotNull(LocalTrackRecord::year).firstOrNull()
            val candidates = albums.values.filter { album ->
                sameName(album.title, title) && (year == null || album.year == null || year == album.year) && albumArtists[album.id] == names
            }
            val remote = candidates.filter { !it.isLocal }.singleOrNull()
            val remoteId = tracks.mapNotNull(LocalTrackRecord::remoteAlbumId).distinct().singleOrNull()
            val local = candidates.firstOrNull { it.isLocal && it.id in previousIds } ?: candidates.firstOrNull { it.isLocal }
            val id = remoteId ?: remote?.id ?: local?.id ?: "LOCAL_ALBUM_${stableHash(albumKey(first))}"
            val existing = database.albumEntity(id)
            val localAlbum = existing?.isLocal ?: (remoteId == null && remote == null)
            val thumbnail = tracks.firstNotNullOfOrNull { it.thumbnailUrl }
                ?: tracks.firstNotNullOfOrNull { it.mediaStoreThumbnailUrl }
            val duration = tracks.sumOf { it.durationSeconds.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val entity = if (existing != null) {
                if (existing.isLocal) existing.copy(
                    title = title,
                    year = year,
                    thumbnailUrl = thumbnail ?: existing.thumbnailUrl,
                    songCount = tracks.size,
                    duration = duration,
                ) else existing.copy(year = existing.year ?: year, thumbnailUrl = existing.thumbnailUrl ?: thumbnail)
            } else AlbumEntity(
                id = id,
                title = title,
                year = year,
                thumbnailUrl = thumbnail,
                songCount = tracks.size,
                duration = duration,
                isLocal = localAlbum,
            )
            database.deleteLocalMusicAlias(id, "album")
            database.upsert(entity)
            if (localAlbum || existing == null || database.albumArtistEntities(id).isEmpty()) {
                if (localAlbum) database.deleteAlbumArtistMapsByAlbumIds(listOf(id))
                artistIds.forEachIndexed { index, artistId -> database.insert(AlbumArtistMap(albumId = id, artistId = artistId, order = index)) }
            }
            candidates.filter { it.isLocal && it.id != id }.forEach { obsolete ->
                database.mergeLocalAlbum(obsolete.id, id)
                albums.remove(obsolete.id)
                albumArtists.remove(obsolete.id)
            }
            albums[id] = database.albumEntity(id) ?: entity
            albumArtists[id] = database.albumArtistEntities(id).map { LocalMusicIdentity.normalize(it.name) }.toSet()
            return id
        }
    }

    private suspend fun loadSongs(ids: List<String>): Map<String, Song> =
        ids.chunked(SqlBatchSize).flatMap { database.getSongsByIds(it) }.associateBy { it.song.id }

    private suspend fun loadLyrics(ids: List<String>): Map<String, LyricsEntity> =
        ids.chunked(SqlBatchSize).flatMap { database.getLyricsByIds(it) }.associateBy { it.id }

    private suspend fun loadFormats(ids: List<String>): Map<String, FormatEntity> =
        ids.chunked(SqlBatchSize).flatMap { database.getFormatsByIds(it) }.associateBy { it.id }

    private fun isUnchanged(track: LocalTrackRecord, song: SongEntity?, format: FormatEntity?): Boolean =
        song != null && format != null && track.dateModified != null &&
            song.dateModified == track.dateModified && format.contentLength == track.sizeBytes

    @Suppress("DEPRECATION")
    private suspend fun queryTracks(scanConfig: LocalSongScanConfig, canReadMediaStore: Boolean): List<LocalTrackRecord> {
        val included = scanConfig.sanitizedIncludedFolders.map { LocalMusicFolder.fromEntry(it) }
        val excluded = scanConfig.sanitizedExcludedFolders.map { LocalMusicFolder.fromEntry(it) }
        val readableTrees = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
        val previousDocumentIds = database.localSongIds().mapNotNull { id ->
            val uri = Uri.parse(id)
            val identity = LocalMusicFolder.documentIdentity(uri) ?: return@mapNotNull null
            if (!DocumentsContract.isTreeUri(uri)) return@mapNotNull null
            val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri))
            if (treeUri in readableTrees) identity to id else null
        }.toMap()
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.ALBUM_ARTIST)
            add(MediaStore.MediaColumns.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.VOLUME_NAME)
            }
        }.toTypedArray()
        val selection = buildList {
            add("${MediaStore.Audio.Media.SIZE} > 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add("${MediaStore.MediaColumns.IS_TRASHED} = 0")
        }.joinToString(" AND ")
        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownTitle = context.getString(R.string.unknown)
        val minimumDuration = scanConfig.sanitizedMinimumDurationSeconds
        val candidates = mutableListOf<MediaStoreCandidate>()
        val lyricsExtractor = EmbeddedLyricsExtractor(context.contentResolver)
        val cursor = if (canReadMediaStore) {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC, ${MediaStore.Audio.Media._ID} ASC",
            ) ?: throw IOException("MediaStore returned no audio cursor")
        } else null
        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) it.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
            val durationIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearIndex = it.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val trackIndex = it.getColumnIndex(MediaStore.Audio.Media.TRACK)
            val modifiedIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val relativeIndex = it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataIndex = it.getColumnIndex(MediaStore.MediaColumns.DATA)
            val volumeIndex = it.getColumnIndex(MediaStore.MediaColumns.VOLUME_NAME)
            while (it.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val folder = LocalMusicFolder.fromMediaStore(
                    it.getStringOrNull(relativeIndex), it.getStringOrNull(dataIndex), it.getStringOrNull(volumeIndex),
                )
                if ((included.isNotEmpty() && included.none { entry -> entry.contains(folder) }) ||
                    excluded.any { entry -> entry.contains(folder) }
                ) continue
                val displayName = it.getStringOrNull(displayNameIndex)
                val mime = it.getStringOrNull(mimeIndex)?.takeIf(String::isNotBlank) ?: "audio/*"
                if (!SupportedLocalAudio.isSupported(displayName, mime)) continue
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.getLong(idIndex))
                val identity = folder.fileIdentity(displayName.orEmpty())
                candidates += MediaStoreCandidate(
                    id = previousDocumentIds[identity] ?: uri.toString(),
                    uri = uri,
                    identity = identity,
                    displayName = displayName,
                    mimeType = mime,
                    dateModifiedSeconds = it.getLong(modifiedIndex),
                    sizeBytes = it.getLong(sizeIndex).coerceAtLeast(0L),
                    durationSeconds = (it.getLong(durationIndex).coerceAtLeast(0L) / 1000L)
                        .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    title = cleanTag(it.getStringOrNull(titleIndex)),
                    artist = cleanTag(it.getStringOrNull(artistIndex)),
                    album = cleanTag(it.getStringOrNull(albumIndex)),
                    albumArtist = cleanTag(it.getStringOrNull(albumArtistIndex)),
                    encodedTrack = it.getIntOrNull(trackIndex)?.takeIf { value -> value > 0 },
                    year = it.getIntOrNull(yearIndex)?.takeIf { value -> value in 1..9999 },
                    albumId = it.getLongOrNull(albumIdIndex)?.takeIf { value -> value > 0L },
                )
            }
        }
        val treeUris = scanConfig.sanitizedIncludedFolders.mapNotNull { LocalMusicFolder.treeUri(it) }
        return coroutineScope {
            val treeDocuments = treeUris.map { treeUri -> async { foldersRepository.audioDocuments(treeUri, excluded) } }
            val mediaStoreTracks = candidates.mapConcurrently(FileScanParallelism) { candidate ->
                readMediaStoreTrack(candidate, minimumDuration, unknownTitle, unknownArtist, lyricsExtractor)
            }
            val discoveredFiles = candidates.zip(mediaStoreTracks).mapNotNullTo(hashSetOf()) { (candidate, track) ->
                candidate.identity.takeIf { track != null }
            }
            val visitedDocuments = hashSetOf<String>()
            val documents = treeDocuments.awaitAll().flatten().mapNotNull { document ->
                val identity = LocalMusicFolder.documentIdentity(document.uri) ?: document.uri.toString()
                if (!visitedDocuments.add(identity) || identity in discoveredFiles) null
                else (previousDocumentIds[identity] ?: document.uri.toString()) to document
            }
            val documentTracks = documents.mapConcurrently(FileScanParallelism) { (id, document) ->
                readDocumentTrack(id, document, minimumDuration, unknownTitle, unknownArtist, lyricsExtractor)
            }
            mediaStoreTracks.filterNotNull() + documentTracks.filterNotNull()
        }
    }

    private suspend fun readMediaStoreTrack(
        candidate: MediaStoreCandidate,
        minimumDuration: Int,
        unknownTitle: String,
        unknownArtist: String,
        lyricsExtractor: EmbeddedLyricsExtractor,
    ): LocalTrackRecord? {
        val metadata = metadataReader.read(candidate.uri, candidate.dateModifiedSeconds, candidate.sizeBytes)
        val duration = metadata.durationSeconds ?: candidate.durationSeconds
        if (duration < minimumDuration) return null
        val artist = metadata.artist ?: candidate.artist
        val album = metadata.album ?: candidate.album
        val encodedTrack = candidate.encodedTrack
        return LocalTrackRecord(
            id = candidate.id,
            title = metadata.title ?: candidate.title
                ?: candidate.displayName?.substringBeforeLast('.')?.takeIf(String::isNotBlank) ?: unknownTitle,
            artists = artist?.let(::splitArtistNames).orEmpty().ifEmpty { listOf(unknownArtist) },
            albumName = album,
            albumArtist = metadata.albumArtist ?: candidate.albumArtist,
            trackNumber = metadata.trackNumber ?: encodedTrack?.rem(1000)?.takeIf { it > 0 },
            discNumber = metadata.discNumber ?: encodedTrack?.div(1000)?.takeIf { it > 0 },
            durationSeconds = duration,
            year = metadata.year ?: candidate.year,
            dateModified = epochSecondsToDateTime(candidate.dateModifiedSeconds),
            sizeBytes = candidate.sizeBytes,
            mimeType = candidate.mimeType,
            thumbnailUrl = metadata.thumbnailUrl,
            mediaStoreThumbnailUrl = candidate.albumId?.takeIf { album != null }
                ?.let { ContentUris.withAppendedId(AlbumArtUri, it).toString() },
            embeddedLyrics = extractEmbeddedLyrics(lyricsExtractor, candidate.uri, candidate.displayName, candidate.mimeType),
        )
    }

    private suspend fun readDocumentTrack(
        id: String,
        document: LocalAudioDocument,
        minimumDuration: Int,
        unknownTitle: String,
        unknownArtist: String,
        lyricsExtractor: EmbeddedLyricsExtractor,
    ): LocalTrackRecord? {
        val uri = Uri.parse(id)
        val metadata = metadataReader.read(uri, document.dateModifiedSeconds, document.sizeBytes)
        val duration = metadata.durationSeconds ?: 0
        if (duration < minimumDuration) return null
        return LocalTrackRecord(
            id = id,
            title = metadata.title ?: document.displayName?.substringBeforeLast('.')
                ?.takeIf(String::isNotBlank) ?: unknownTitle,
            artists = metadata.artist?.let(::splitArtistNames).orEmpty().ifEmpty { listOf(unknownArtist) },
            albumName = metadata.album,
            albumArtist = metadata.albumArtist,
            trackNumber = metadata.trackNumber,
            discNumber = metadata.discNumber,
            durationSeconds = duration,
            year = metadata.year,
            dateModified = epochSecondsToDateTime(document.dateModifiedSeconds),
            sizeBytes = document.sizeBytes,
            mimeType = document.mimeType,
            thumbnailUrl = metadata.thumbnailUrl,
            mediaStoreThumbnailUrl = null,
            embeddedLyrics = extractEmbeddedLyrics(lyricsExtractor, uri, document.displayName, document.mimeType),
        )
    }

    private suspend fun extractEmbeddedLyrics(
        extractor: EmbeddedLyricsExtractor,
        uri: Uri,
        displayName: String?,
        mimeType: String,
    ): String? {
        currentCoroutineContext().ensureActive()
        return extractor.extract(contentUri = uri, displayName = displayName, mimeType = mimeType)
            ?.let(LyricsUtils::lyricsOrNotFound)?.takeIf { it != LyricsEntity.LYRICS_NOT_FOUND }
    }

    private fun epochSecondsToDateTime(seconds: Long): LocalDateTime? =
        seconds.takeIf { it > 0L }?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()) }

    private fun cleanTag(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && it.lowercase(Locale.ROOT) !in UnknownTags
    }

    private fun splitArtistNames(value: String): List<String> =
        value.split(';', '\u0000').mapNotNull(::cleanTag).distinctBy(LocalMusicIdentity::normalize)

    private fun albumArtistNames(track: LocalTrackRecord): List<String> =
        track.albumArtist?.let(::splitArtistNames)?.takeIf { it.isNotEmpty() } ?: track.artists

    private fun albumKey(track: LocalTrackRecord): String =
        "${LocalMusicIdentity.normalize(track.albumName.orEmpty())}|${albumArtistNames(track).map(LocalMusicIdentity::normalize).sorted().joinToString("\u0000")}"

    private fun sameName(first: String, second: String): Boolean =
        LocalMusicIdentity.normalize(first) == LocalMusicIdentity.normalize(second)

    private fun stableHash(source: String): String =
        UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8)).toString().replace("-", "")

    private fun pruneUnusedArtworkFiles(retainedArtworkFileNames: Set<String>) {
        val directory = File(context.filesDir, LocalArtworkDirectoryName)
        directory.listFiles()?.filter { it.isFile && it.name !in retainedArtworkFileNames }?.forEach {
            if (!it.delete()) Timber.tag(LogTag).w("Failed to delete stale local artwork: %s", it.name)
        }
    }

    private fun updateEmbeddedLyrics(
        track: LocalTrackRecord,
        existingLyrics: LyricsEntity?,
    ) {
        val embeddedLyrics = track.embeddedLyrics
        if (embeddedLyrics != null) {
            if (existingLyrics == null || existingLyrics.hasGenericSource()) {
                database.upsert(
                    LyricsEntity(
                        id = track.id,
                        lyrics = embeddedLyrics,
                        source = LyricsEntity.Source.EMBEDDED.value,
                    ),
                )
            }
            return
        }

        if (existingLyrics?.source == LyricsEntity.Source.EMBEDDED.value) {
            database.delete(existingLyrics)
        }
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? =
        if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else null

    private fun android.database.Cursor.getIntOrNull(columnIndex: Int): Int? =
        if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? =
        if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null

    private data class EnrichedTracks(val tracks: List<LocalTrackRecord>, val lookupFailed: Boolean)

    private data class EnrichmentPlan(
        val track: LocalTrackRecord,
        val query: LocalMetadataQuery,
        val lookupArtist: String?,
        val hasTrackArtist: Boolean,
        val lookupEligible: Boolean,
    )

    private data class MediaStoreCandidate(
        val id: String,
        val uri: Uri,
        val identity: String,
        val displayName: String?,
        val mimeType: String,
        val dateModifiedSeconds: Long,
        val sizeBytes: Long,
        val durationSeconds: Int,
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val encodedTrack: Int?,
        val year: Int?,
        val albumId: Long?,
    )

    private data class LocalTrackRecord(
        val id: String,
        val title: String,
        val artists: List<String>,
        val albumName: String?,
        val albumArtist: String?,
        val trackNumber: Int?,
        val discNumber: Int?,
        val durationSeconds: Int,
        val year: Int?,
        val dateModified: LocalDateTime?,
        val sizeBytes: Long,
        val mimeType: String,
        val thumbnailUrl: String?,
        val mediaStoreThumbnailUrl: String?,
        val embeddedLyrics: String?,
        val remoteArtistId: String? = null,
        val matchedArtistName: String? = null,
        val artistThumbnailUrl: String? = null,
        val remoteAlbumId: String? = null,
    )

    private companion object {
        val AlbumArtUri: Uri = Uri.parse("content://media/external/audio/albumart")
        val UnknownTags = setOf("<unknown>", "unknown", "unknown artist", "unknown album", "unknown title")
        const val LocalArtworkDirectoryName = "local_music_artwork"
        const val LogTag = "LocalSongScanner"
        const val SqlBatchSize = 900
        const val MetadataLookupParallelism = 4
        val FileScanParallelism = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
    }
}
