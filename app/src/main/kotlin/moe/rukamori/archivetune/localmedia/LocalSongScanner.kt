/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
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
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class LocalSongScanConfig(
    val minimumDurationSeconds: Int = 0,
    val includedFolders: Set<String> = emptySet(),
    val excludedFolders: Set<String> = emptySet(),
) {
    val sanitizedMinimumDurationSeconds: Int
        get() = minimumDurationSeconds.coerceAtLeast(0)

    val sanitizedIncludedFolders: Set<String>
        get() = deduplicateFolderEntries(includedFolders)

    val sanitizedExcludedFolders: Set<String>
        get() = deduplicateFolderEntries(excludedFolders)

    companion object {
        private val DuplicateSlashRegex = Regex("/+")

        fun normalizeFolderEntry(raw: String): String {
            return raw
                .trim()
                .replace('\\', '/')
                .replace(DuplicateSlashRegex, "/")
                .trim('/')
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

data class LocalSongScanSummary(
    val scannedSongs: Int,
    val removedSongs: Int,
)

class LocalSongScanner
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    suspend fun scanDevice(scanConfig: LocalSongScanConfig = LocalSongScanConfig()): LocalSongScanSummary = withContext(Dispatchers.IO) {
        val snapshot = queryTracks(scanConfig)
        database.withTransaction {
            val existingLocalIds = localSongIds()
            val scannedIds = snapshot.tracks.map(LocalTrackRecord::id)
            val scannedIdSet = scannedIds.toSet()
            val removedIds = existingLocalIds.filterNot(scannedIdSet::contains)

            if (scannedIds.isEmpty()) {
                clearLocalSongs()
            } else {
                removedIds.chunked(SqlBatchSize).forEach(::deleteSongsByIds)
            }

            val existingSongs = loadSongs(scannedIds)
            val existingLyrics = loadLyrics(scannedIds)
            val existingArtists = loadArtists(snapshot.artists.map(LocalArtistRecord::id))
            val existingAlbums = loadAlbums(snapshot.albums.map(LocalAlbumRecord::id))

            snapshot.artists.forEach { artist ->
                val existingArtist = existingArtists[artist.id]
                upsert(
                    ArtistEntity(
                        id = artist.id,
                        name = artist.name,
                        thumbnailUrl = existingArtist?.thumbnailUrl,
                        channelId = null,
                        lastUpdateTime = existingArtist?.lastUpdateTime ?: LocalDateTime.now(),
                        bookmarkedAt = existingArtist?.bookmarkedAt,
                        isLocal = true,
                    ),
                )
            }

            snapshot.albums.forEach { album ->
                val existingAlbum = existingAlbums[album.id]
                upsert(
                    AlbumEntity(
                        id = album.id,
                        playlistId = null,
                        title = album.title,
                        year = album.year ?: existingAlbum?.year,
                        thumbnailUrl = album.thumbnailUrl,
                        themeColor = existingAlbum?.themeColor,
                        songCount = album.songCount,
                        duration = album.duration,
                        explicit = false,
                        lastUpdateTime = LocalDateTime.now(),
                        bookmarkedAt = existingAlbum?.bookmarkedAt,
                        likedDate = existingAlbum?.likedDate,
                        inLibrary = existingAlbum?.inLibrary,
                        isLocal = true,
                    ),
                )
            }

            snapshot.albums.map(LocalAlbumRecord::id).distinct().chunked(SqlBatchSize).forEach(::deleteAlbumArtistMapsByAlbumIds)
            snapshot.albums.forEach { album ->
                album.artistIds.forEachIndexed { index, artistId ->
                    insert(
                        AlbumArtistMap(
                            albumId = album.id,
                            artistId = artistId,
                            order = index,
                        ),
                    )
                }
            }

            snapshot.tracks.forEach { track ->
                val existingSong = existingSongs[track.id]?.song
                upsert(
                    SongEntity(
                        id = track.id,
                        title = track.title,
                        duration = track.durationSeconds,
                        thumbnailUrl = track.thumbnailUrl,
                        albumId = track.albumId,
                        albumName = track.albumName,
                        explicit = existingSong?.explicit ?: false,
                        year = track.year ?: existingSong?.year,
                        date = existingSong?.date,
                        dateModified = track.dateModified ?: existingSong?.dateModified,
                        liked = existingSong?.liked ?: false,
                        likedDate = existingSong?.likedDate,
                        totalPlayTime = existingSong?.totalPlayTime ?: 0L,
                        inLibrary = null,
                        dateDownload = existingSong?.dateDownload,
                        isLocal = true,
                    ),
                )
                upsert(
                    FormatEntity(
                        id = track.id,
                        itag = -1,
                        mimeType = track.mimeType,
                        codecs = "",
                        bitrate = 0,
                        sampleRate = null,
                        contentLength = track.sizeBytes,
                        loudnessDb = null,
                        perceptualLoudnessDb = null,
                        playbackUrl = null,
                    ),
                )
                deleteSongArtistMaps(track.id)
                track.artists.forEachIndexed { index, artist ->
                    insert(
                        SongArtistMap(
                            songId = track.id,
                            artistId = artist.id,
                            position = index,
                        ),
                    )
                }
                deleteSongAlbumMaps(track.id)
                track.albumId?.let { albumId ->
                    insert(
                        SongAlbumMap(
                            songId = track.id,
                            albumId = albumId,
                            index = 0,
                        ),
                    )
                }
                updateEmbeddedLyrics(track, existingLyrics[track.id])
            }

            pruneLocalAlbums()
            pruneLocalArtists()
            pruneFormats()
            prunePlayCounts()

            LocalSongScanSummary(
                scannedSongs = snapshot.tracks.size,
                removedSongs = removedIds.size,
            )
        }
    }

        private suspend fun loadSongs(ids: List<String>): Map<String, Song> =
        ids.chunked(SqlBatchSize)
            .flatMap { chunk -> database.getSongsByIds(chunk) }
            .associateBy { item -> item.song.id }

        private suspend fun loadLyrics(ids: List<String>): Map<String, LyricsEntity> =
        ids.chunked(SqlBatchSize)
            .flatMap { chunk -> database.getLyricsByIds(chunk) }
            .associateBy { item -> item.id }

        private suspend fun loadArtists(ids: List<String>): Map<String, ArtistEntity> =
        ids.distinct().chunked(SqlBatchSize)
            .flatMap { chunk -> database.getArtistEntitiesByIds(chunk) }
            .associateBy { item -> item.id }

        private suspend fun loadAlbums(ids: List<String>): Map<String, AlbumEntity> =
        ids.distinct().chunked(SqlBatchSize)
            .flatMap { chunk -> database.getAlbumEntitiesByIds(chunk) }
            .associateBy { item -> item.id }

    @Suppress("DEPRECATION")
    private fun queryTracks(scanConfig: LocalSongScanConfig): LocalScanSnapshot {
        val sanitizedMinimumDurationMs = scanConfig.sanitizedMinimumDurationSeconds.toLong() * 1000L
        val sanitizedIncludedFolders = scanConfig.sanitizedIncludedFolders
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        val sanitizedExcludedFolders = scanConfig.sanitizedExcludedFolders
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ARTIST_ID)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
            } else {
                add(MediaStore.MediaColumns.DATA)
            }
        }.toTypedArray()
        val selection = buildList {
            add("${MediaStore.Audio.Media.SIZE} > 0")
            if (sanitizedMinimumDurationMs > 0L) {
                add("${MediaStore.Audio.Media.DURATION} >= $sanitizedMinimumDurationMs")
            } else {
                add("${MediaStore.Audio.Media.DURATION} > 0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add("${MediaStore.MediaColumns.IS_PENDING} = 0")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add("is_trashed = 0")
            }
        }.joinToString(" AND ")

        val unknownArtist = context.getString(R.string.unknown_artist)
        val unknownTitle = context.getString(R.string.unknown)
        val tracks = mutableListOf<LocalTrackRecord>()
        val retainedArtworkFileNames = linkedSetOf<String>()
        val embeddedLyricsExtractor = EmbeddedLyricsExtractor(context.contentResolver)
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC, ${MediaStore.Audio.Media._ID} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val dataPathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idIndex)
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                val normalizedFolderPath = resolveNormalizedFolderPath(
                    relativePath = cursor.getStringOrNull(relativePathIndex),
                    absolutePath = cursor.getStringOrNull(dataPathIndex),
                )
                if (!shouldIncludeFolder(normalizedFolderPath, sanitizedIncludedFolders)) {
                    continue
                }
                if (shouldExcludeFolder(normalizedFolderPath, sanitizedExcludedFolders)) {
                    continue
                }
                val displayName = cursor.getString(displayNameIndex)
                val mimeType = cursor.getString(mimeTypeIndex)?.takeIf(String::isNotBlank) ?: "audio/*"
                if (!SupportedLocalAudio.isSupported(displayName, mimeType)) {
                    continue
                }
                val artistValue = normalizeArtistName(cursor.getString(artistIndex), unknownArtist)
                val splitArtists = splitArtistNames(artistValue).ifEmpty { listOf(unknownArtist) }
                val mediaStoreArtistId = cursor.getLongOrNull(artistIdIndex)
                val artists = splitArtists.mapIndexed { index, name ->
                    LocalArtistRecord(
                        id = buildArtistId(mediaStoreArtistId, name, index, splitArtists.size),
                        name = name,
                    )
                }
                val mediaStoreAlbumId = cursor.getLongOrNull(albumIdIndex)
                val albumName = normalizeAlbumName(cursor.getString(albumIndex))
                val title = normalizeTitle(
                    title = cursor.getString(titleIndex),
                    displayName = displayName,
                    fallback = unknownTitle,
                )
                val dateModifiedSeconds = cursor.getLong(dateModifiedIndex)
                val sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L)
                val thumbnailUrl = resolveTrackThumbnail(
                    contentUri = contentUri,
                    albumName = albumName,
                    mediaStoreAlbumId = mediaStoreAlbumId,
                    dateModifiedSeconds = dateModifiedSeconds,
                    sizeBytes = sizeBytes,
                    retainedArtworkFileNames = retainedArtworkFileNames,
                )
                val embeddedLyrics = embeddedLyricsExtractor.extract(
                    contentUri = contentUri,
                    displayName = displayName,
                    mimeType = mimeType,
                )?.let(LyricsUtils::lyricsOrNotFound)
                    ?.takeIf { lyrics -> lyrics != LyricsEntity.LYRICS_NOT_FOUND }
                tracks += LocalTrackRecord(
                    id = contentUri.toString(),
                    title = title,
                    artists = artists,
                    albumId = albumName?.let {
                        buildAlbumId(
                            mediaStoreAlbumId = mediaStoreAlbumId,
                            albumName = it,
                            primaryArtistId = artists.firstOrNull()?.id,
                        )
                    },
                    albumName = albumName,
                    durationSeconds = (cursor.getLong(durationIndex).coerceAtLeast(0L) / 1000L)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
                    year = cursor.getIntOrNull(yearIndex)?.takeIf { it > 0 },
                    dateModified = dateModifiedSeconds
                        .takeIf { it > 0L }
                        ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()) },
                    sizeBytes = sizeBytes,
                    mimeType = mimeType,
                    thumbnailUrl = thumbnailUrl,
                    embeddedLyrics = embeddedLyrics,
                )
            }
        }
        pruneUnusedArtworkFiles(retainedArtworkFileNames)

        val albums = tracks
            .filter { !it.albumId.isNullOrBlank() && !it.albumName.isNullOrBlank() }
            .groupBy { it.albumId!! }
            .map { (albumId, albumTracks) ->
                LocalAlbumRecord(
                    id = albumId,
                    title = albumTracks.first().albumName.orEmpty(),
                    year = albumTracks.mapNotNull(LocalTrackRecord::year).maxOrNull(),
                    thumbnailUrl = albumTracks.mapNotNull(LocalTrackRecord::thumbnailUrl).firstOrNull(),
                    songCount = albumTracks.size,
                    duration = albumTracks.sumOf(LocalTrackRecord::durationSeconds),
                    artistIds = albumTracks.flatMap { track -> track.artists.map(LocalArtistRecord::id) }.distinct(),
                )
            }

        return LocalScanSnapshot(
            tracks = tracks,
            artists = tracks.flatMap(LocalTrackRecord::artists).distinctBy(LocalArtistRecord::id),
            albums = albums,
        )
    }

    private fun normalizeTitle(title: String?, displayName: String?, fallback: String): String {
        return title?.trim()?.takeIf { it.isNotBlank() }
            ?: displayName?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun resolveTrackThumbnail(
        contentUri: Uri,
        albumName: String?,
        mediaStoreAlbumId: Long?,
        dateModifiedSeconds: Long,
        sizeBytes: Long,
        retainedArtworkFileNames: MutableSet<String>,
    ): String? {
        return extractEmbeddedArtwork(
            contentUri = contentUri,
            dateModifiedSeconds = dateModifiedSeconds,
            sizeBytes = sizeBytes,
            retainedArtworkFileNames = retainedArtworkFileNames,
        ) ?: mediaStoreAlbumId
            ?.takeIf { !albumName.isNullOrBlank() }
            ?.takeIf { it > 0L }
            ?.let { ContentUris.withAppendedId(AlbumArtUri, it).toString() }
    }

    private fun extractEmbeddedArtwork(
        contentUri: Uri,
        dateModifiedSeconds: Long,
        sizeBytes: Long,
        retainedArtworkFileNames: MutableSet<String>,
    ): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, contentUri)
            val artworkBytes = retriever.embeddedPicture ?: return null
            val extension = artworkBytes.imageExtension() ?: return null
            val fileName = "${stableHash("$contentUri|$dateModifiedSeconds|$sizeBytes")}.$extension"
            val artworkDirectory = localArtworkDirectory()
            val artworkFile = File(artworkDirectory, fileName)
            retainedArtworkFileNames += fileName
            if (!artworkFile.exists() || artworkFile.length() != artworkBytes.size.toLong()) {
                artworkDirectory.mkdirs()
                FileOutputStream(artworkFile).use { outputStream ->
                    outputStream.write(artworkBytes)
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                artworkFile,
            ).toString()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Timber.tag(LogTag).w(error, "Failed to extract embedded artwork for %s", contentUri)
            null
        } finally {
            runCatching { retriever.release() }
                .onFailure { error -> Timber.tag(LogTag).w(error, "Failed to release artwork retriever") }
        }
    }

    private fun updateEmbeddedLyrics(track: LocalTrackRecord, existingLyrics: LyricsEntity?) {
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

    private fun pruneUnusedArtworkFiles(retainedArtworkFileNames: Set<String>) {
        val artworkDirectory = localArtworkDirectory()
        if (!artworkDirectory.exists()) return
        artworkDirectory.listFiles()
            ?.filter { file -> file.isFile && file.name !in retainedArtworkFileNames }
            ?.forEach { file ->
                if (!file.delete()) {
                    Timber.tag(LogTag).w("Failed to delete stale local artwork: %s", file.name)
                }
            }
    }

    private fun localArtworkDirectory(): File {
        return File(context.filesDir, LocalArtworkDirectoryName)
    }

    private fun ByteArray.imageExtension(): String? {
        return when {
            size >= 3 &&
                this[0] == 0xFF.toByte() &&
                this[1] == 0xD8.toByte() &&
                this[2] == 0xFF.toByte() -> "jpg"

            size >= 8 &&
                this[0] == 0x89.toByte() &&
                this[1] == 0x50.toByte() &&
                this[2] == 0x4E.toByte() &&
                this[3] == 0x47.toByte() &&
                this[4] == 0x0D.toByte() &&
                this[5] == 0x0A.toByte() &&
                this[6] == 0x1A.toByte() &&
                this[7] == 0x0A.toByte() -> "png"

            size >= 12 &&
                this[0] == 0x52.toByte() &&
                this[1] == 0x49.toByte() &&
                this[2] == 0x46.toByte() &&
                this[3] == 0x46.toByte() &&
                this[8] == 0x57.toByte() &&
                this[9] == 0x45.toByte() &&
                this[10] == 0x42.toByte() &&
                this[11] == 0x50.toByte() -> "webp"

            else -> null
        }
    }

    private fun normalizeArtistName(rawArtist: String?, fallback: String): String {
        val normalized = rawArtist?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        return normalized ?: fallback
    }

    private fun normalizeAlbumName(rawAlbum: String?): String? {
        return rawAlbum?.trim()?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
    }

    private fun splitArtistNames(rawArtist: String): List<String> {
        return rawArtist
            .split(ArtistSeparators)
            .map(String::trim)
            .filter(String::isNotBlank)
            .ifEmpty { listOf(rawArtist) }
    }

    private fun buildArtistId(
        mediaStoreArtistId: Long?,
        artistName: String,
        index: Int,
        totalArtists: Int,
    ): String {
        val stableId = mediaStoreArtistId?.takeIf { it > 0L }
        return if (stableId != null && totalArtists == 1) {
            "LOCAL_ARTIST_$stableId"
        } else {
            "LOCAL_ARTIST_${stableHash("$artistName|$index")}"
        }
    }

    private fun buildAlbumId(
        mediaStoreAlbumId: Long?,
        albumName: String,
        primaryArtistId: String?,
    ): String {
        val stableId = mediaStoreAlbumId?.takeIf { it > 0L }
        return if (stableId != null) {
            "LOCAL_ALBUM_$stableId"
        } else {
            "LOCAL_ALBUM_${stableHash("$albumName|$primaryArtistId")}"
        }
    }

    private fun stableHash(source: String): String {
        return UUID.nameUUIDFromBytes(source.toByteArray(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "")
    }

    private fun resolveNormalizedFolderPath(relativePath: String?, absolutePath: String?): String? {
        val relativeFolder = LocalSongScanConfig.normalizeFolderEntry(relativePath.orEmpty())
        if (relativeFolder.isNotEmpty()) {
            return relativeFolder.lowercase(Locale.ROOT)
        }

        val absoluteFolder = absolutePath
            ?.replace('\\', '/')
            ?.substringBeforeLast('/', missingDelimiterValue = "")
            .orEmpty()
        val normalizedAbsoluteFolder = LocalSongScanConfig.normalizeFolderEntry(absoluteFolder)
        return normalizedAbsoluteFolder.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT)
    }

    private fun shouldIncludeFolder(folderPath: String?, includedFolders: Set<String>): Boolean {
        if (includedFolders.isEmpty()) return true
        return matchesFolderEntry(folderPath, includedFolders)
    }

    private fun shouldExcludeFolder(folderPath: String?, excludedFolders: Set<String>): Boolean {
        if (excludedFolders.isEmpty()) return false
        return matchesFolderEntry(folderPath, excludedFolders)
    }

    private fun matchesFolderEntry(folderPath: String?, folders: Set<String>): Boolean {
        if (folderPath.isNullOrEmpty()) return false
        return folders.any { folder ->
            folderPath == folder ||
                folderPath.startsWith("$folder/") ||
                folderPath.endsWith("/$folder") ||
                folderPath.contains("/$folder/")
        }
    }

    private fun android.database.Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getLong(columnIndex) else null
    }

    private fun android.database.Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getInt(columnIndex) else null
    }

    private fun android.database.Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (columnIndex >= 0 && !isNull(columnIndex)) getString(columnIndex) else null
    }

    private data class LocalScanSnapshot(
        val tracks: List<LocalTrackRecord>,
        val artists: List<LocalArtistRecord>,
        val albums: List<LocalAlbumRecord>,
    )

    private data class LocalTrackRecord(
        val id: String,
        val title: String,
        val artists: List<LocalArtistRecord>,
        val albumId: String?,
        val albumName: String?,
        val durationSeconds: Int,
        val year: Int?,
        val dateModified: LocalDateTime?,
        val sizeBytes: Long,
        val mimeType: String,
        val thumbnailUrl: String?,
        val embeddedLyrics: String?,
    )

    private data class LocalArtistRecord(
        val id: String,
        val name: String,
    )

    private data class LocalAlbumRecord(
        val id: String,
        val title: String,
        val year: Int?,
        val thumbnailUrl: String?,
        val songCount: Int,
        val duration: Int,
        val artistIds: List<String>,
    )

    private companion object {
        val AlbumArtUri: Uri = Uri.parse("content://media/external/audio/albumart")
        val ArtistSeparators = Regex("[,;/&]")
        const val LocalArtworkDirectoryName = "local_music_artwork"
        const val LogTag = "LocalSongScanner"
        const val SqlBatchSize = 900
    }
}
