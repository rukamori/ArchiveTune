/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.storage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.exoplayer.offline.Download
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DownloadedSongsFolderTreeUriKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class DownloadedSongExporter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        @DownloadCache private val downloadCache: Cache,
        private val m4aAudioConverter: M4aAudioConverter,
        private val lyricsHelper: LyricsHelper,
    ) {
        private val exportCoordinator = SongExportCoordinator()

        suspend fun export(download: Download): Boolean =
            export(
                songId = download.request.id,
                fallbackTitle = download.request.data.toString(Charsets.UTF_8).takeIf(String::isNotBlank),
                completedContentLength = download.contentLength.takeIf { it > 0L },
            )

        suspend fun export(
            songId: String,
            fallbackTitle: String? = null,
            completedContentLength: Long? = null,
        ): Boolean =
            withContext(Dispatchers.IO) {
                exportCoordinator.run(songId) export@{
                    try {
                        val song = loadSongForExport(songId)
                        val format = song?.format
                        val expectedContentLength =
                            completedContentLength
                                ?: format?.contentLength?.takeIf { it > 0L }
                                ?: return@export false
                        val cachedSpans = downloadCache.exportableSpans(songId)
                        if (cachedSpans.isEmpty()) return@export false

                        val baseMetadata = ExportedSongMetadata.from(songId, song, fallbackTitle, context)
                        val fileName =
                            buildExportFileName(
                                metadata = baseMetadata,
                                mimeType = format?.mimeType,
                            )
                        return@export coroutineScope {
                            val metadata =
                                async {
                                    val lyrics = resolveExportLyrics(songId, song)
                                    baseMetadata.copy(
                                        lyrics = lyrics?.lyrics,
                                        lyricsSource = lyrics?.source,
                                        synchronizedLyrics = lyrics?.lyrics?.hasSynchronizedLyrics() == true,
                                    )
                                }
                            val artworkBytes = async { fetchArtworkBytes(baseMetadata.thumbnailUrl) }
                            val selectedTreeUri = selectedTreeUri()
                            if (selectedTreeUri != null) {
                                return@coroutineScope exportToSelectedFolder(
                                    treeUri = selectedTreeUri,
                                    songId = songId,
                                    fileName = fileName,
                                    metadata = metadata,
                                    format = format,
                                    spans = cachedSpans,
                                    expectedContentLength = expectedContentLength,
                                    artworkBytes = artworkBytes,
                                )
                            }

                            val targetDirectory = StorageLocationRepository.exportedDownloadsDirectory(context)
                            if (!targetDirectory.ensureWritableDirectory()) return@coroutineScope false

                            val targetFile = targetDirectory.resolve(fileName)
                            val copied =
                                copyCachedSpans(
                                    spans = cachedSpans,
                                    targetFile = targetFile,
                                    expectedContentLength = expectedContentLength,
                                    metadata = metadata,
                                    format = format,
                                    artworkBytes = artworkBytes,
                                )
                            if (!copied) return@coroutineScope false
                            deleteExistingExports(
                                directory = targetDirectory,
                                songId = songId,
                                expectedBaseName = fileName.substringBeforeLast('.'),
                                except = targetFile,
                            )
                            MediaScannerConnection.scanFile(
                                context,
                                arrayOf(targetFile.absolutePath),
                                arrayOf(M4aMimeType),
                                null,
                            )
                            true
                        }
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        Timber.tag(LogTag).w(throwable, "Failed to export downloaded song %s", songId)
                        false
                    }
                }
            }

        /** Explicitly removes an exported copy. Download removal intentionally does not call this. */
        suspend fun removeExport(songId: String): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    selectedTreeUri()?.let { treeUri ->
                        deleteExistingTreeExports(
                            treeUri = treeUri,
                            songId = songId,
                            expectedBaseName = null,
                        )
                    }
                }.onFailure { throwable ->
                    Timber.tag(LogTag).w(throwable, "Failed to remove exported tree song %s", songId)
                }
                val targetDirectory = StorageLocationRepository.exportedDownloadsDirectory(context)
                if (!targetDirectory.exists()) return@withContext true
                val metadata = loadSongForExport(songId)?.let { song -> ExportedSongMetadata.from(songId, song, null, context) }
                deleteExistingExports(
                    directory = targetDirectory,
                    songId = songId,
                    expectedBaseName = metadata?.let { buildExportBaseName(it, songId) },
                    except = null,
                )
            }

        private fun Cache.exportableSpans(songId: String): List<CacheSpan> =
            runCatching {
                getCachedSpans(songId)
                    .filter { span -> span.isCached && span.length > 0L && span.file?.isFile == true }
                    .sortedBy { span -> span.position }
            }.getOrDefault(emptyList())

        private suspend fun loadSongForExport(songId: String): Song? {
            repeat(MetadataLoadRetryCount) {
                val song = database.getSongById(songId)
                if (song?.format != null) return song
                delay(MetadataLoadRetryDelayMillis)
            }
            return database.getSongById(songId)
        }

        private suspend fun resolveExportLyrics(
            songId: String,
            song: Song?,
        ): LyricsEntity? {
            val storedLyrics = database.getLyricsById(songId)
            if (storedLyrics != null) return storedLyrics.toExportLyrics()
            if (song == null) return null

            return try {
                val lyrics = lyricsHelper.getLyrics(song.toLyricsMediaMetadata())
                if (lyrics.isBlank() || lyrics == LyricsEntity.LYRICS_NOT_FOUND) return null
                LyricsEntity(
                    id = songId,
                    lyrics = lyrics,
                    source = LyricsEntity.Source.REMOTE.value,
                ).also { entity ->
                    database.query { insertLyricsIfAbsent(entity.id, entity.lyrics, entity.source, entity.updatedAt) }
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Timber.tag(LogTag).w(throwable, "Failed to resolve lyrics for exported song %s", songId)
                null
            }
        }

        private fun Song.toLyricsMediaMetadata(): MediaMetadata {
            val albumId = album?.id ?: song.albumId
            val albumTitle = album?.title ?: song.albumName
            return MediaMetadata(
                id = id,
                title = title,
                artists =
                    artists.map { artist ->
                        MediaMetadata.Artist(
                            id = artist.id,
                            name = artist.name,
                            thumbnailUrl = artist.thumbnailUrl,
                        )
                    },
                duration = song.duration,
                thumbnailUrl = thumbnailUrl,
                album =
                    if (!albumId.isNullOrBlank() && !albumTitle.isNullOrBlank()) {
                        MediaMetadata.Album(albumId, albumTitle)
                    } else {
                        null
                    },
                explicit = song.explicit,
                liked = song.liked,
                likedDate = song.likedDate,
                inLibrary = song.inLibrary,
            )
        }

        private suspend fun selectedTreeUri(): Uri? =
            context
                .dataStore
                .data
                .first()[DownloadedSongsFolderTreeUriKey]
                ?.takeIf(String::isNotBlank)
                ?.toUri()

        private suspend fun fetchArtworkBytes(thumbnailUrl: String?): ByteArray? {
            if (thumbnailUrl.isNullOrBlank()) return null
            return runCatching {
                val request = ImageRequest.Builder(context)
                    .data(thumbnailUrl)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.image.toBitmap()
                    ByteArrayOutputStream().use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                        outputStream.toByteArray()
                    }
                } else {
                    null
                }
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                null
            }
        }

        private fun File.isPlayableM4a(): Boolean =
            isStructurallyValidM4a() &&
                runCatching {
                    MediaExtractor().useAndCheckForAudio { extractor ->
                        extractor.setDataSource(absolutePath)
                    }
                }.getOrDefault(false)

        private fun Uri.isPlayableM4aDocument(): Boolean =
            runCatching {
                context.contentResolver.openAssetFileDescriptor(this, "r")?.use { descriptor ->
                    MediaExtractor().useAndCheckForAudio { extractor ->
                        if (descriptor.length >= 0L) {
                            extractor.setDataSource(
                                descriptor.fileDescriptor,
                                descriptor.startOffset,
                                descriptor.length,
                            )
                        } else {
                            extractor.setDataSource(descriptor.fileDescriptor)
                        }
                    }
                } == true
            }.getOrDefault(false)

        private inline fun MediaExtractor.useAndCheckForAudio(
            setDataSource: (MediaExtractor) -> Unit,
        ): Boolean =
            try {
                setDataSource(this)
                (0 until trackCount).any { trackIndex ->
                    val trackFormat = getTrackFormat(trackIndex)
                    if (trackFormat.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") != true) {
                        return@any false
                    }
                    selectTrack(trackIndex)
                    val durationUs =
                        if (trackFormat.containsKey(android.media.MediaFormat.KEY_DURATION)) {
                            trackFormat.getLong(android.media.MediaFormat.KEY_DURATION)
                        } else {
                            0L
                        }
                    if (durationUs > 0L) seekTo(durationUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    val sampleSize = readSampleData(ByteBuffer.allocate(MaxAudioSampleValidationBytes), 0)
                    val sampleTimestampUs = sampleTime
                    unselectTrack(trackIndex)
                    sampleSize > 0 &&
                        sampleTimestampUs >= 0L &&
                        (durationUs <= 0L || durationUs - sampleTimestampUs <= MaxAudioTailGapMicros)
                }
            } finally {
                release()
            }

        private suspend fun exportToSelectedFolder(
            treeUri: Uri,
            songId: String,
            fileName: String,
            metadata: Deferred<ExportedSongMetadata>,
            format: FormatEntity?,
            spans: List<CacheSpan>,
            expectedContentLength: Long,
            artworkBytes: Deferred<ByteArray?>,
        ): Boolean {
            return runCatching {
                val resolver = context.contentResolver
                val parentUri = treeUri.toDocumentUri()
                val tempFile =
                    File.createTempFile(
                        "archivetune-export-",
                        M4aFileSuffix,
                        context.cacheDir,
                    )
                val copied =
                    try {
                        copyCachedSpans(
                            spans = spans,
                            targetFile = tempFile,
                            expectedContentLength = expectedContentLength,
                            metadata = metadata,
                            format = format,
                            artworkBytes = artworkBytes,
                        )
                    } catch (throwable: Throwable) {
                        tempFile.delete()
                        throw throwable
                    }
                if (!copied) {
                    tempFile.delete()
                    return@runCatching false
                }
                val audioUri =
                    DocumentsContract.createDocument(
                        resolver,
                        parentUri,
                        M4aMimeType,
                        ".archivetune-${System.nanoTime()}-${fileName}",
                    ) ?: run {
                        tempFile.delete()
                        return@runCatching false
                    }
                val written =
                    resolver.openOutputStream(audioUri, "w")?.use { outputStream ->
                        val bytesWritten = tempFile.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
                        bytesWritten == tempFile.length()
                    } ?: false
                if (!written) {
                    tempFile.delete()
                    runCatching { DocumentsContract.deleteDocument(resolver, audioUri) }
                    return@runCatching false
                }
                val persistedSize = resolver.query(audioUri, arrayOf(Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                } ?: -1L
                if (persistedSize != tempFile.length()) {
                    tempFile.delete()
                    runCatching { DocumentsContract.deleteDocument(resolver, audioUri) }
                    return@runCatching false
                }
                tempFile.delete()
                commitSafReplacement(
                    stagingDocument = audioUri,
                    finalName = fileName,
                    renameDocument = { documentUri, displayName ->
                        DocumentsContract.renameDocument(resolver, documentUri, displayName)
                    },
                    deleteStagingDocument = { documentUri ->
                        runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }.getOrDefault(false)
                    },
                    deletePreviousDocuments = { replacementUri ->
                        deleteExistingTreeExports(
                            treeUri = treeUri,
                            songId = songId,
                            expectedBaseName = fileName.substringBeforeLast('.'),
                            except = replacementUri,
                        )
                    },
                )
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                false
            }
        }

        private suspend fun copyCachedSpans(
            spans: List<CacheSpan>,
            targetFile: File,
            expectedContentLength: Long,
            metadata: Deferred<ExportedSongMetadata>,
            format: FormatEntity?,
            artworkBytes: Deferred<ByteArray?>,
        ): Boolean =
            runCatching {
                targetFile.parentFile?.mkdirs()
                val sourceFile =
                    targetFile.resolveSibling(
                        "${targetFile.name}.source-${System.nanoTime()}.${sourceFileExtension(format?.mimeType)}",
                    )
                val m4aFile = targetFile.resolveSibling("${targetFile.name}.tmp-${System.nanoTime()}$M4aFileSuffix")
                try {
                    val exportedBytes = sourceFile.outputStream().use { outputStream -> copyCachedSpansTo(spans, outputStream) }
                    if (!isCompleteExport(exportedBytes, expectedContentLength) || sourceFile.length() != expectedContentLength) {
                        return@runCatching false
                    }
                    val converted =
                        if (canCopyDirectlyToM4a(format)) {
                            replaceAtomically(sourceFile, m4aFile)
                            true
                        } else {
                            m4aAudioConverter.convert(sourceFile, m4aFile)
                    }
                    if (!converted || !m4aFile.isStructurallyValidM4a()) return@runCatching false
                    val resolvedMetadata = metadata.await()
                    val resolvedArtwork = artworkBytes.await()
                    if (!resolvedMetadata.thumbnailUrl.isNullOrBlank() && resolvedArtwork == null) {
                        Timber.tag(LogTag).w("Artwork could not be resolved for exported song %s", resolvedMetadata.id)
                        return@runCatching false
                    }
                    if (!writeEmbeddedMetadata(m4aFile, resolvedMetadata, resolvedArtwork)) return@runCatching false
                    if (!m4aFile.isPlayableM4a()) return@runCatching false
                    replaceAtomically(m4aFile, targetFile)
                    targetFile.isFile && targetFile.isStructurallyValidM4a()
                } finally {
                    sourceFile.delete()
                    m4aFile.delete()
                }
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                false
            }

        private fun writeEmbeddedMetadata(
            audioFile: File,
            metadata: ExportedSongMetadata,
            artworkBytes: ByteArray?,
        ): Boolean =
            runCatching {
                audioFile.writeMp4Metadata(metadata, artworkBytes)
            }.onFailure { throwable ->
                Timber.tag(LogTag).w(throwable, "Failed to embed metadata in %s", audioFile.name)
            }.getOrDefault(false)

        private fun deleteExistingExports(
            directory: File,
            songId: String,
            expectedBaseName: String?,
            except: File?,
        ): Boolean {
            val marker = exportIdMarker(songId)
            var deleted = true
            directory
                .listFiles()
                ?.filter { file ->
                    file.isFile &&
                        (
                            file.name.contains(marker) ||
                                (expectedBaseName != null && file.name.nameWithoutKnownAudioExtension() == expectedBaseName)
                        )
                }
                ?.forEach { file ->
                    if (except != null && file.canonicalPath == except.canonicalPath) return@forEach
                    if (!runCatching { file.delete() || !file.exists() }.getOrDefault(false)) {
                        deleted = false
                    }
            }
            return deleted
        }

        private fun deleteExistingTreeExports(
            treeUri: Uri,
            songId: String,
            expectedBaseName: String?,
            except: Uri? = null,
        ): Boolean {
            val marker = exportIdMarker(songId)
            val resolver = context.contentResolver
            val childrenUri = treeUri.toChildDocumentsUri()
            var deleted = true
            resolver
                .query(
                    childrenUri,
                    arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(nameIndex) ?: continue
                        val matchingName =
                            expectedBaseName != null &&
                                displayName.nameWithoutKnownAudioExtension() == expectedBaseName
                        if (!displayName.contains(marker) && !matchingName) continue
                        val documentUri = treeUri.toChildDocumentUri(cursor.getString(idIndex))
                        if (documentUri == except) continue
                        if (!runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }.getOrDefault(false)) {
                            deleted = false
                        }
                    }
                }
            return deleted
        }

        suspend fun isAlreadyExported(download: Download): Boolean =
            withContext(Dispatchers.IO) {
                val songId = download.request.id
                val marker = exportIdMarker(songId)
                val internalDir = StorageLocationRepository.exportedDownloadsDirectory(context)
                val treeUri = selectedTreeUri()
                if (treeUri != null) {
                    val resolver = context.contentResolver
                    resolver.query(
                        treeUri.toChildDocumentsUri(),
                        arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                        val nameIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)
                        while (cursor.moveToNext()) {
                            val displayName = cursor.getString(nameIndex) ?: continue
                            if (
                                displayName.contains(marker) &&
                                displayName.endsWith(M4aFileSuffix, ignoreCase = true) &&
                                cursor.getLong(sizeIndex) > 0L
                            ) {
                                val documentUri = treeUri.toChildDocumentUri(cursor.getString(idIndex))
                                if (documentUri.isPlayableM4aDocument()) {
                                    return@withContext true
                                }
                            }
                        }
                    }
                    return@withContext false
                }
                val exportedInternally =
                    internalDir.listFiles()?.any { file ->
                        file.isFile &&
                            file.name.contains(marker) &&
                            file.name.endsWith(M4aFileSuffix, ignoreCase = true) &&
                            file.isPlayableM4a()
                    } == true
                exportedInternally
            }

        private companion object {
            const val LogTag = "DownloadedSongExporter"
            const val MetadataLoadRetryCount = 5
            const val MetadataLoadRetryDelayMillis = 200L
        }
    }

internal fun buildExportFileName(
    metadata: ExportedSongMetadata,
    mimeType: String?,
): String {
    val baseName = buildExportBaseName(metadata, metadata.id)
    return "$baseName.${exportFileExtension(mimeType)}"
}

internal fun buildExportBaseName(
    metadata: ExportedSongMetadata,
    id: String,
): String {
    val title = metadata.title.toFileNamePart(maxLength = 96)
    val artist = metadata.artists.joinToString(", ").toFileNamePart(maxLength = 72)
    return "$title - $artist ${exportIdMarker(id)}"
}

internal fun exportIdMarker(id: String): String = "[${id.toFileNamePart(maxLength = 48)}]"

internal fun isCompleteExport(
    exportedBytes: Long,
    completedContentLength: Long,
): Boolean = completedContentLength > 0L && exportedBytes == completedContentLength

/**
 * Serializes exports for the same song without dropping a later destination change.
 * Different songs can still export concurrently on the caller's coroutine dispatcher.
 */
internal class SongExportCoordinator {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> run(
        songId: String,
        block: suspend () -> T,
    ): T = locks.computeIfAbsent(songId) { Mutex() }.withLock { block() }
}

/**
 * Commits a verified SAF staging document with failure-atomic semantics.
 *
 * SAF has no cross-provider atomic-replace primitive. The strongest portable guarantee is
 * therefore to rename the verified staging document before deleting any previous document.
 * A failure always leaves either the previous export or the verified replacement available.
 */
internal fun <T> commitSafReplacement(
    stagingDocument: T,
    finalName: String,
    renameDocument: (T, String) -> T?,
    deleteStagingDocument: (T) -> Boolean,
    deletePreviousDocuments: (replacementDocument: T) -> Boolean,
): Boolean {
    val replacementDocument = renameDocument(stagingDocument, finalName)
    if (replacementDocument == null) {
        deleteStagingDocument(stagingDocument)
        return false
    }
    if (!deletePreviousDocuments(replacementDocument)) {
        // Keep the verified replacement: deleting it would turn cleanup failure into data loss.
        return false
    }
    // Providers may suffix the first rename while the old document exists. Normalize if supported.
    renameDocument(replacementDocument, finalName)
    return true
}

private fun replaceAtomically(
    source: File,
    target: File,
) {
    try {
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        val backup = target.resolveSibling("${target.name}.backup-${System.nanoTime()}")
        val hadTarget = target.exists()
        try {
            if (hadTarget) Files.move(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            backup.delete()
        } catch (throwable: Throwable) {
            if (hadTarget && backup.exists()) {
                Files.move(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            throw throwable
        }
    }
}

private fun String.toFileNamePart(maxLength: Int): String {
    val normalized =
        replace(UnsafeFileNameCharacters, "_")
            .replace(WhitespaceRegex, " ")
            .trim(' ', '.')
    return normalized
        .takeIf(String::isNotBlank)
        ?.take(maxLength)
        ?.trim(' ', '.')
        ?.takeIf(String::isNotBlank)
        ?: "Unknown"
}

private val MimeTypeToSourceExtensionMap = mapOf(
    "audio/aac" to "aac",
    "audio/aac-adts" to "aac",
    "audio/x-aac" to "aac",
    "audio/flac" to "flac",
    "audio/x-flac" to "flac",
    "audio/mpeg" to "mp3",
    "audio/x-mpeg" to "mp3",
    "audio/ogg" to "ogg",
    "audio/opus" to "ogg",
    "application/ogg" to "ogg",
    "audio/webm" to "webm",
    "video/webm" to "webm",
    "audio/wav" to "wav",
    "audio/wave" to "wav",
    "audio/x-wav" to "wav",
)

private val Mp4SourceMimeTypes = setOf("audio/mp4", "video/mp4", "application/mp4", "audio/x-m4a")

private fun sourceFileExtension(mimeType: String?): String =
    MimeTypeToSourceExtensionMap[mimeType.normalizedMimeType()] ?: "bin"

internal fun exportFileExtension(@Suppress("UNUSED_PARAMETER") mimeType: String?): String = M4aFileExtension

internal fun exportMimeType(@Suppress("UNUSED_PARAMETER") mimeType: String?): String = M4aMimeType

internal fun canCopyDirectlyToM4a(format: FormatEntity?): Boolean {
    if (format?.mimeType.normalizedMimeType() !in Mp4SourceMimeTypes) return false
    val codec = "${format?.codecs.orEmpty()} ${format?.mimeType.orEmpty()}".lowercase(Locale.ROOT)
    return codec.contains("mp4a") || codec.contains("aac") || codec.contains("alac")
}

private fun File.writeId3Metadata(
    metadata: ExportedSongMetadata,
    format: FormatEntity?,
    artworkBytes: ByteArray?,
): Boolean = runCatching {
    var existingTagSize = 0L
    inputStream().use { inputStream ->
        val header = ByteArray(Id3HeaderSize)
        val bytesRead = inputStream.read(header)
        if (bytesRead == Id3HeaderSize && header.copyOfRange(0, 3).decodeToString() == "ID3") {
            existingTagSize = header.readId3SyncSafeInt(offset = 6).toLong() + Id3HeaderSize
        }
    }

    val tag = buildId3Tag(metadata, format, artworkBytes)
    val tempFile = File.createTempFile("archivetune-id3-", ".tmp", parentFile)
    try {
        tempFile.outputStream().use { outputStream ->
            outputStream.write(tag)
            inputStream().use { inputStream ->
                if (existingTagSize > 0) {
                    inputStream.skipFully(existingTagSize)
                }
                inputStream.copyTo(outputStream)
            }
        }
        if (exists() && !delete()) {
            tempFile.delete()
            return@runCatching false
        }
        if (!tempFile.renameTo(this)) {
            tempFile.copyTo(this, overwrite = true)
            tempFile.delete()
        }
        true
    } catch (throwable: Throwable) {
        tempFile.delete()
        throw throwable
    }
}.getOrDefault(false)

internal fun File.writeMp4Metadata(
    metadata: ExportedSongMetadata,
    artworkBytes: ByteArray?,
): Boolean = runCatching {
    val atoms = readTopLevelMp4Atoms()
    val moov = atoms.firstOrNull { atom -> atom.type == "moov" } ?: return@runCatching false
    val mdat = atoms.firstOrNull { atom -> atom.type == "mdat" }

    val fileSize = length()
    if (moov.end > fileSize || moov.headerSize >= moov.size) return@runCatching false

    val oldMoov = ByteArray(moov.size)
    inputStream().use { inputStream ->
        inputStream.skipFully(moov.start.toLong())
        inputStream.readFully(oldMoov, 0, moov.size)
    }

    val oldPayload = oldMoov.copyOfRange(moov.headerSize, oldMoov.size)
    val newPayload =
        ByteArrayOutputStream().use { outputStream ->
            outputStream.write(oldPayload)
            outputStream.write(buildMp4UdtaAtom(metadata, artworkBytes))
            outputStream.toByteArray()
        }
    var newMoov = buildMp4Atom("moov".toIso8859Bytes(), newPayload)
    if (mdat != null && moov.start < mdat.start) {
        val delta = newMoov.size - oldMoov.size
        if (delta > 0) {
            newMoov = newMoov.copyOf()
            newMoov.adjustMp4ChunkOffsets(delta.toLong())
        }
    }

    val tempFile = File.createTempFile("archivetune-mp4-", ".tmp", parentFile)
    try {
        tempFile.outputStream().use { outputStream ->
            if (moov.start > 0) {
                inputStream().use { inputStream ->
                    inputStream.copyLimitedTo(outputStream, moov.start.toLong())
                }
            }
            outputStream.write(newMoov)
            val remainingBytes = fileSize - moov.end
            if (remainingBytes > 0) {
                inputStream().use { inputStream ->
                    inputStream.skipFully(moov.end.toLong())
                    inputStream.copyTo(outputStream)
                }
            }
        }
        if (exists() && !delete()) {
            tempFile.delete()
            return@runCatching false
        }
        if (!tempFile.renameTo(this)) {
            tempFile.copyTo(this, overwrite = true)
            tempFile.delete()
        }
        true
    } catch (throwable: Throwable) {
        tempFile.delete()
        throw throwable
    }
}.getOrDefault(false)

internal fun File.readTopLevelMp4Atoms(): List<Mp4Atom> {
    val atoms = mutableListOf<Mp4Atom>()
    var offset = 0
    val fileSize = length().toInt()
    inputStream().use { inputStream ->
        val header = ByteArray(16)
        while (offset + 8 <= fileSize) {
            inputStream.readFully(header, 0, 8)
            val smallSize = ((header[0].toLong() and 0xFF) shl 24) or
                           ((header[1].toLong() and 0xFF) shl 16) or
                           ((header[2].toLong() and 0xFF) shl 8) or
                           (header[3].toLong() and 0xFF)
            val type = header.copyOfRange(4, 8).decodeToString()
            val headerSize: Int
            val size: Long
            if (smallSize == 1L) {
                inputStream.readFully(header, 8, 8)
                headerSize = 16
                size = ((header[8].toLong() and 0xFF) shl 56) or
                       ((header[9].toLong() and 0xFF) shl 48) or
                       ((header[10].toLong() and 0xFF) shl 40) or
                       ((header[11].toLong() and 0xFF) shl 32) or
                       ((header[12].toLong() and 0xFF) shl 24) or
                       ((header[13].toLong() and 0xFF) shl 16) or
                       ((header[14].toLong() and 0xFF) shl 8) or
                       (header[15].toLong() and 0xFF)
            } else {
                headerSize = 8
                size = if (smallSize == 0L) (fileSize - offset).toLong() else smallSize
            }
            atoms += Mp4Atom(
                start = offset,
                headerSize = headerSize,
                size = size.toInt(),
                type = type
            )
            val payloadToSkip = size - headerSize
            if (payloadToSkip > 0) {
                inputStream.skipFully(payloadToSkip)
            }
            offset += size.toInt()
        }
    }
    return atoms
}

internal fun File.isStructurallyValidM4a(): Boolean =
    runCatching {
        if (!isFile || length() < Mp4MinimumFileSizeBytes || length() > Int.MAX_VALUE) return@runCatching false
        val atoms = readTopLevelMp4Atoms()
        atoms.isNotEmpty() &&
            atoms.first().type == "ftyp" &&
            atoms.any { atom -> atom.type == "moov" } &&
            atoms.any { atom -> atom.type == "mdat" } &&
            atoms.all { atom -> atom.size >= atom.headerSize && atom.start >= 0 && atom.end.toLong() <= length() } &&
            atoms.last().end.toLong() == length()
    }.getOrDefault(false)

private fun InputStream.readFully(buffer: ByteArray, offset: Int, length: Int) {
    var totalRead = 0
    while (totalRead < length) {
        val read = read(buffer, offset + totalRead, length - totalRead)
        if (read == -1) throw java.io.EOFException("Reached end of stream before reading $length bytes")
        totalRead += read
    }
}

internal fun buildId3Tag(
    metadata: ExportedSongMetadata,
    format: FormatEntity?,
    artworkBytes: ByteArray?,
): ByteArray {
    val frames =
        listOfNotNull(
            id3TextFrame("TIT2", metadata.title),
            id3TextFrame("TPE1", metadata.artistText),
            metadata.album?.let { album -> id3TextFrame("TALB", album) },
            metadata.downloadedAt?.let { downloadedAt -> id3TextFrame("TDRC", downloadedAt) },
            metadata.durationSeconds.takeIf { duration -> duration > 0 }?.let { duration ->
                id3TextFrame("TLEN", (duration * 1000L).toString())
            },
            format?.mimeType?.let { mimeType -> id3TextFrame("TMED", mimeType) },
            id3TextFrame("TSSE", "ArchiveTune"),
            id3UserTextFrame(description = "ArchiveTune ID", value = metadata.id),
            artworkBytes?.let { bytes -> id3PictureFrame(bytes) }
        )
    val payload =
        ByteArrayOutputStream().use { outputStream ->
            frames.forEach(outputStream::write)
            outputStream.toByteArray()
        }
    return ByteArrayOutputStream().use { outputStream ->
        outputStream.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0))
        outputStream.write(payload.size.toId3SyncSafeBytes())
        outputStream.write(payload)
        outputStream.toByteArray()
    }
}

internal fun id3TextFrame(
    id: String,
    value: String,
): ByteArray = id3Frame(id, byteArrayOf(Id3Utf8EncodingByte) + value.toUtf8Bytes())

internal fun id3UserTextFrame(
    description: String,
    value: String,
): ByteArray =
    id3Frame(
        id = "TXXX",
        payload =
            byteArrayOf(Id3Utf8EncodingByte) +
                description.toUtf8Bytes() +
                byteArrayOf(0) +
                value.toUtf8Bytes(),
    )

internal fun id3Frame(
    id: String,
    payload: ByteArray,
): ByteArray =
    ByteArrayOutputStream().use { outputStream ->
        outputStream.write(id.toIso8859Bytes())
        outputStream.write(payload.size.toId3SyncSafeBytes())
        outputStream.write(byteArrayOf(0, 0))
        outputStream.write(payload)
        outputStream.toByteArray()
    }

private fun buildMp4UdtaAtom(
    metadata: ExportedSongMetadata,
    artworkBytes: ByteArray?,
): ByteArray =
    buildMp4Atom(
        "udta".toByteArray(),
        buildMp4Atom(
            "meta".toByteArray(),
            ByteArrayOutputStream().use { outputStream ->
                outputStream.write(byteArrayOf(0, 0, 0, 0))
                outputStream.write(buildMp4HandlerAtom())
                outputStream.write(buildMp4IlstAtom(metadata, artworkBytes))
                outputStream.toByteArray()
            },
        ),
    )

private fun buildMp4HandlerAtom(): ByteArray =
    buildMp4Atom(
        "hdlr".toByteArray(),
        ByteArrayOutputStream().use { outputStream ->
            outputStream.write(byteArrayOf(0, 0, 0, 0))
            outputStream.writeInt(0)
            outputStream.write("mdir".toByteArray())
            outputStream.writeInt(0)
            outputStream.writeInt(0)
            outputStream.writeInt(0)
            outputStream.write("appl\u0000".toByteArray())
            outputStream.toByteArray()
        },
    )

internal fun buildMp4IlstAtom(
    metadata: ExportedSongMetadata,
    artworkBytes: ByteArray?,
): ByteArray =
    buildMp4Atom(
        "ilst".toByteArray(),
        ByteArrayOutputStream().use { outputStream ->
            outputStream.writeMp4TextItem(Mp4TitleAtom, metadata.title)
            outputStream.writeMp4TextItem(Mp4ArtistAtom, metadata.artistText)
            outputStream.writeMp4TextItem(Mp4AlbumArtistAtom, metadata.artistText)
            metadata.album?.let { album -> outputStream.writeMp4TextItem(Mp4AlbumAtom, album) }
            metadata.releaseDate?.let { releaseDate -> outputStream.writeMp4TextItem(Mp4DayAtom, releaseDate) }
            metadata.lyrics?.let { lyrics -> outputStream.writeMp4TextItem(Mp4LyricsAtom, lyrics) }
            metadata.thumbnailUrl?.let { thumbnailUrl -> outputStream.writeMp4TextItem(Mp4UrlAtom, thumbnailUrl) }
            outputStream.writeMp4TextItem(Mp4EncoderAtom, "ArchiveTune")
            outputStream.writeMp4TextItem(Mp4CommentAtom, "ArchiveTune ID: ${metadata.id}")
            outputStream.writeMp4FreeformTextItem("ArchiveTune ID", metadata.id)
            metadata.downloadedAt?.let { value -> outputStream.writeMp4FreeformTextItem("Downloaded At", value) }
            metadata.lyricsSource?.let { value -> outputStream.writeMp4FreeformTextItem("Lyrics Source", value) }
            if (metadata.lyrics != null) {
                outputStream.writeMp4FreeformTextItem(
                    "Lyrics Type",
                    if (metadata.synchronizedLyrics) "Synchronized LRC" else "Unsynchronized",
                )
            }
            outputStream.writeMp4FreeformTextItem("Explicit", metadata.explicit.toString())
            metadata.originalMimeType?.let { value -> outputStream.writeMp4FreeformTextItem("Original MIME Type", value) }
            metadata.originalCodec?.let { value -> outputStream.writeMp4FreeformTextItem("Original Codec", value) }
            metadata.originalBitrate?.let { value -> outputStream.writeMp4FreeformTextItem("Original Bitrate", value.toString()) }
            artworkBytes?.let { bytes -> outputStream.writeMp4ImageItem(Mp4CoverAtom, bytes) }
            outputStream.toByteArray()
        },
    )

private fun ByteArrayOutputStream.writeMp4TextItem(
    type: ByteArray,
    value: String,
) {
    if (value.isBlank()) return
    write(
        buildMp4Atom(
            type,
            buildMp4Atom(
                "data".toIso8859Bytes(),
                ByteArrayOutputStream().use { outputStream ->
                    outputStream.writeInt(Mp4TextTypeFlag)
                    outputStream.writeInt(Mp4DefaultLocale)
                    outputStream.write(value.toUtf8Bytes())
                    outputStream.toByteArray()
                },
            ),
        ),
    )
}

private fun ByteArrayOutputStream.writeMp4FreeformTextItem(
    name: String,
    value: String,
) {
    if (name.isBlank() || value.isBlank()) return
    val payload =
        ByteArrayOutputStream().use { outputStream ->
            outputStream.write(
                buildMp4Atom(
                    "mean".toIso8859Bytes(),
                    byteArrayOf(0, 0, 0, 0) + Mp4FreeformNamespace.toUtf8Bytes(),
                ),
            )
            outputStream.write(
                buildMp4Atom(
                    "name".toIso8859Bytes(),
                    byteArrayOf(0, 0, 0, 0) + name.toUtf8Bytes(),
                ),
            )
            outputStream.write(
                buildMp4Atom(
                    "data".toIso8859Bytes(),
                    ByteArrayOutputStream().use { dataOutput ->
                        dataOutput.writeInt(Mp4TextTypeFlag)
                        dataOutput.writeInt(Mp4DefaultLocale)
                        dataOutput.write(value.toUtf8Bytes())
                        dataOutput.toByteArray()
                    },
                ),
            )
            outputStream.toByteArray()
        }
    write(buildMp4Atom(Mp4FreeformAtom, payload))
}

private fun ByteArrayOutputStream.writeMp4ImageItem(
    type: ByteArray,
    imageBytes: ByteArray,
) {
    if (imageBytes.isEmpty()) return
    write(
        buildMp4Atom(
            type,
            buildMp4Atom(
                "data".toIso8859Bytes(),
                ByteArrayOutputStream().use { outputStream ->
                    outputStream.writeInt(Mp4ImageTypeFlag) // Type indicator: JPEG/PNG image is 13
                    outputStream.writeInt(Mp4DefaultLocale)
                    outputStream.write(imageBytes)
                    outputStream.toByteArray()
                },
            ),
        ),
    )
}

private fun buildMp4Atom(
    type: ByteArray,
    payload: ByteArray,
): ByteArray =
    ByteArrayOutputStream().use { outputStream ->
        outputStream.writeInt(payload.size + Mp4AtomHeaderSize)
        outputStream.write(type)
        outputStream.write(payload)
        outputStream.toByteArray()
    }

private fun copyCachedSpansTo(
    spans: List<CacheSpan>,
    outputStream: OutputStream,
): Long {
    var expectedPosition = 0L
    spans.forEach { span ->
        val sourceFile = span.file ?: error("Missing cache span file")
        if (span.position > expectedPosition) {
            error("Cache span gap at $expectedPosition for ${span.key}")
        }
        val overlapBytes = (expectedPosition - span.position).coerceAtLeast(0L)
        if (overlapBytes >= span.length) return@forEach
        sourceFile.inputStream().use { inputStream ->
            inputStream.skipFully(overlapBytes)
            inputStream.copyLimitedTo(
                outputStream = outputStream,
                byteCount = span.length - overlapBytes,
            )
        }
        expectedPosition = max(expectedPosition, span.position + span.length)
    }
    return expectedPosition
}

private fun String?.normalizedMimeType(): String =
    this
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()

private fun File.ensureWritableDirectory(): Boolean =
    runCatching {
        if (exists() && !isDirectory) return@runCatching false
        if (!exists() && !mkdirs()) return@runCatching false
        val probe = resolve(".archivetune-download-export-probe")
        probe.writeText("ok")
        probe.delete()
    }.isSuccess

private fun InputStream.skipFully(byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped <= 0L) {
            if (read() == -1) break
            remaining--
        } else {
            remaining -= skipped
        }
    }
}

private fun InputStream.copyLimitedTo(
    outputStream: java.io.OutputStream,
    byteCount: Long,
) {
    val buffer = ByteArray(CopyBufferSizeBytes)
    var remaining = byteCount
    while (remaining > 0L) {
        val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read < 0) break
        outputStream.write(buffer, 0, read)
        remaining -= read
    }
}

private fun Uri.toDocumentUri(): Uri =
    DocumentsContract.buildDocumentUriUsingTree(
        this,
        DocumentsContract.getTreeDocumentId(this),
    )

private fun Uri.toChildDocumentsUri(): Uri =
    DocumentsContract.buildChildDocumentsUriUsingTree(
        this,
        DocumentsContract.getTreeDocumentId(this),
    )

private fun Uri.toChildDocumentUri(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(this, documentId)

private fun ByteArray.readId3SyncSafeInt(offset: Int): Int =
    ((this[offset].toInt() and 0x7F) shl 21) or
        ((this[offset + 1].toInt() and 0x7F) shl 14) or
        ((this[offset + 2].toInt() and 0x7F) shl 7) or
        (this[offset + 3].toInt() and 0x7F)

private fun Int.toId3SyncSafeBytes(): ByteArray =
    byteArrayOf(
        ((this shr 21) and 0x7F).toByte(),
        ((this shr 14) and 0x7F).toByte(),
        ((this shr 7) and 0x7F).toByte(),
        (this and 0x7F).toByte(),
    )

internal fun ByteArray.readMp4Atoms(
    start: Int,
    end: Int,
): List<Mp4Atom> {
    val atoms = mutableListOf<Mp4Atom>()
    var offset = start
    while (offset + Mp4AtomHeaderSize <= end) {
        val smallSize = readUInt32(offset)
        val type = copyOfRange(offset + 4, offset + 8).decodeToString()
        val headerSize: Int
        val size: Long
        when (smallSize) {
            0L -> {
                headerSize = Mp4AtomHeaderSize
                size = (end - offset).toLong()
            }

            1L -> {
                if (offset + Mp4ExtendedAtomHeaderSize > end) break
                headerSize = Mp4ExtendedAtomHeaderSize
                size = readUInt64(offset + 8)
            }

            else -> {
                headerSize = Mp4AtomHeaderSize
                size = smallSize
            }
        }
        if (size < headerSize || offset + size > end) break
        atoms +=
            Mp4Atom(
                start = offset,
                headerSize = headerSize,
                size = size.toInt(),
                type = type,
            )
        offset += size.toInt()
    }
    return atoms
}

internal fun ByteArray.adjustMp4ChunkOffsets(delta: Long) {
    adjustMp4ChunkOffsets(
        start = Mp4AtomHeaderSize,
        end = size,
        delta = delta,
    )
}

internal fun ByteArray.adjustMp4ChunkOffsets(
    start: Int,
    end: Int,
    delta: Long,
) {
    readMp4Atoms(start, end).forEach { atom ->
        when (atom.type) {
            "stco" -> adjustStcoAtom(atom, delta)
            "co64" -> adjustCo64Atom(atom, delta)
            else -> {
                if (atom.type in Mp4ContainerAtoms) {
                    val childStart =
                        atom.start +
                            atom.headerSize +
                            if (atom.type == "meta") Mp4FullBoxHeaderSize else 0
                    if (childStart < atom.end) {
                        adjustMp4ChunkOffsets(
                            start = childStart,
                            end = atom.end,
                            delta = delta,
                        )
                    }
                }
            }
        }
    }
}

internal fun ByteArray.adjustStcoAtom(
    atom: Mp4Atom,
    delta: Long,
) {
    val entryCountOffset = atom.start + atom.headerSize + Mp4FullBoxHeaderSize
    if (entryCountOffset + 4 > atom.end) return
    val entryCount = readUInt32(entryCountOffset).toInt()
    var offset = entryCountOffset + 4
    repeat(entryCount) {
        if (offset + 4 > atom.end) return
        val newOffset = readUInt32(offset) + delta
        require(newOffset <= MaxUnsignedInt32) { "Chunk offset overflow in M4A file: $newOffset" }
        writeIntAt(offset, newOffset.toInt())
        offset += 4
    }
}

internal fun ByteArray.adjustCo64Atom(
    atom: Mp4Atom,
    delta: Long,
) {
    val entryCountOffset = atom.start + atom.headerSize + Mp4FullBoxHeaderSize
    if (entryCountOffset + 4 > atom.end) return
    val entryCount = readUInt32(entryCountOffset).toInt()
    var offset = entryCountOffset + 4
    repeat(entryCount) {
        if (offset + 8 > atom.end) return
        writeLongAt(offset, readUInt64(offset) + delta)
        offset += 8
    }
}

internal fun ByteArray.readUInt32(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 24) or
        ((this[offset + 1].toLong() and 0xFF) shl 16) or
        ((this[offset + 2].toLong() and 0xFF) shl 8) or
        (this[offset + 3].toLong() and 0xFF)

internal fun ByteArray.readUInt64(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 56) or
        ((this[offset + 1].toLong() and 0xFF) shl 48) or
        ((this[offset + 2].toLong() and 0xFF) shl 40) or
        ((this[offset + 3].toLong() and 0xFF) shl 32) or
        ((this[offset + 4].toLong() and 0xFF) shl 24) or
        ((this[offset + 5].toLong() and 0xFF) shl 16) or
        ((this[offset + 6].toLong() and 0xFF) shl 8) or
        (this[offset + 7].toLong() and 0xFF)

internal fun ByteArray.writeIntAt(
    offset: Int,
    value: Int,
) {
    this[offset] = ((value ushr 24) and 0xFF).toByte()
    this[offset + 1] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 3] = (value and 0xFF).toByte()
}

internal fun ByteArray.writeLongAt(
    offset: Int,
    value: Long,
) {
    this[offset] = ((value ushr 56) and 0xFF).toByte()
    this[offset + 1] = ((value ushr 48) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 40) and 0xFF).toByte()
    this[offset + 3] = ((value ushr 32) and 0xFF).toByte()
    this[offset + 4] = ((value ushr 24) and 0xFF).toByte()
    this[offset + 5] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 6] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 7] = (value and 0xFF).toByte()
}

private fun ByteArrayOutputStream.writeInt(value: Int) {
    write(
        byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte(),
        ),
    )
}

private fun String.nameWithoutKnownAudioExtension(): String {
    val withoutJson = removeSuffix(".json")
    val extension = withoutJson.substringAfterLast('.', missingDelimiterValue = "")
    return if (extension.lowercase(Locale.ROOT) in ExportAudioExtensions) {
        withoutJson.substringBeforeLast('.')
    } else {
        withoutJson
    }
}

internal data class Mp4Atom(
    val start: Int,
    val headerSize: Int,
    val size: Int,
    val type: String,
) {
    val end: Int get() = start + size
}

private val UnsafeFileNameCharacters = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
private val WhitespaceRegex = Regex("\\s+")
private val ExportAudioExtensions = setOf("aac", "flac", "m4a", "mp3", "ogg", "opus", "wav", "webm")
private val SynchronizedLyricsLineRegex = Regex("(?m)^\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?]")
private val Mp4ContainerAtoms = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "udta", "meta")
private val Mp4TitleAtom = byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte())
private val Mp4ArtistAtom = byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte())
private val Mp4AlbumArtistAtom = "aART".toByteArray()
private val Mp4AlbumAtom = byteArrayOf(0xA9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte())
private val Mp4DayAtom = byteArrayOf(0xA9.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 'y'.code.toByte())
private val Mp4LyricsAtom = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
private val Mp4CommentAtom = byteArrayOf(0xA9.toByte(), 'c'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte())
private val Mp4EncoderAtom = byteArrayOf(0xA9.toByte(), 't'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte())
private val Mp4UrlAtom = "purl".toByteArray()
private val Mp4CoverAtom = "covr".toByteArray()
private val Mp4FreeformAtom = "----".toByteArray()
private const val Mp4FreeformNamespace = "com.apple.iTunes"
private const val M4aFileExtension = "m4a"
private const val M4aFileSuffix = ".$M4aFileExtension"
private const val M4aMimeType = "audio/mp4"
private const val Id3HeaderSize = 10
private const val Id3Utf8EncodingByte: Byte = 3
private const val Mp4AtomHeaderSize = 8
private const val Mp4ExtendedAtomHeaderSize = 16
private const val Mp4FullBoxHeaderSize = 4
private const val MaxUnsignedInt32 = 0xFFFF_FFFFL
private const val CopyBufferSizeBytes = 256 * 1024

private const val Mp4TextTypeFlag = 1
private const val Mp4ImageTypeFlag = 13
private const val Mp4DefaultLocale = 0
private const val Mp4MinimumFileSizeBytes = 24L
private const val MaxAudioSampleValidationBytes = 256 * 1024
private const val MaxAudioTailGapMicros = 10_000_000L
private const val Id3Iso88591EncodingByte: Byte = 0
private const val Id3PictureTypeFrontCover: Byte = 3

private fun String.toUtf8Bytes(): ByteArray = toByteArray(Charsets.UTF_8)
private fun String.toIso8859Bytes(): ByteArray = toByteArray(Charsets.ISO_8859_1)

private fun id3PictureFrame(imageBytes: ByteArray): ByteArray {
    val mimeTypeBytes = "image/jpeg".toIso8859Bytes()
    val payload = ByteArrayOutputStream().use { outputStream ->
        outputStream.write(Id3Iso88591EncodingByte.toInt()) // Text encoding: ISO-8859-1
        outputStream.write(mimeTypeBytes)
        outputStream.write(0) // Null terminator for MIME type
        outputStream.write(Id3PictureTypeFrontCover.toInt()) // Picture type: Cover (front)
        outputStream.write(0) // Description null terminator
        outputStream.write(imageBytes)
        outputStream.toByteArray()
    }
    return id3Frame("APIC", payload)
}

internal data class ExportedSongMetadata(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val downloadedAt: String?,
    val releaseDate: String? = null,
    val explicit: Boolean = false,
    val lyrics: String? = null,
    val lyricsSource: String? = null,
    val synchronizedLyrics: Boolean = false,
    val originalMimeType: String? = null,
    val originalCodec: String? = null,
    val originalBitrate: Int? = null,
) {
    val artistText: String get() = artists.joinToString(", ")

    companion object {
        fun from(
            songId: String,
            song: Song?,
            fallbackTitle: String?,
            context: Context,
        ): ExportedSongMetadata {
            val unknownTitle = context.getString(R.string.unknown)
            val unknownArtist = context.getString(R.string.unknown_artist)
            return ExportedSongMetadata(
                id = songId,
                title =
                    song
                        ?.song
                        ?.title
                        ?.takeIf(String::isNotBlank)
                        ?: fallbackTitle
                        ?: unknownTitle,
                artists =
                    song
                        ?.artists
                        ?.mapNotNull { artist -> artist.name.takeIf(String::isNotBlank) }
                        ?.takeIf { artists -> artists.isNotEmpty() }
                        ?: listOf(unknownArtist),
                album = song?.album?.title ?: song?.song?.albumName,
                durationSeconds = song?.song?.duration?.takeIf { duration -> duration > 0 } ?: 0,
                thumbnailUrl = song?.song?.thumbnailUrl,
                downloadedAt =
                    song
                        ?.song
                        ?.dateDownload
                        ?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                releaseDate =
                    song
                        ?.song
                        ?.date
                        ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        ?: song?.song?.year?.toString(),
                explicit = song?.song?.explicit == true,
                originalMimeType = song?.format?.mimeType,
                originalCodec = song?.format?.codecs,
                originalBitrate = song?.format?.bitrate?.takeIf { bitrate -> bitrate > 0 },
            )
        }
    }
}

private fun LyricsEntity?.toExportLyrics(): LyricsEntity? =
    this?.takeIf { entity ->
        entity.lyrics.isNotBlank() && entity.lyrics != LyricsEntity.LYRICS_NOT_FOUND
    }

internal fun String.hasSynchronizedLyrics(): Boolean = SynchronizedLyricsLineRegex.containsMatchIn(this)
