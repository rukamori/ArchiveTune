/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DownloadedSongsFolderTreeUriKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.format.DateTimeFormatter
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
        @PlayerCache private val playerCache: Cache,
    ) {
        private val activeExports = HashSet<String>()

        suspend fun export(download: Download): Boolean =
            export(
                songId = download.request.id,
                fallbackTitle = download.request.data.toString(Charsets.UTF_8).takeIf(String::isNotBlank),
            )

        suspend fun export(
            songId: String,
            fallbackTitle: String? = null,
        ): Boolean =
            withContext(Dispatchers.IO) {
                if (!markExportActive(songId)) return@withContext true
                try {
                    val song = loadSongForExport(songId)
                    val format = song?.format
                    val cachedSpans =
                        downloadCache.exportableSpans(songId)
                            .ifEmpty { playerCache.exportableSpans(songId) }
                    if (cachedSpans.isEmpty()) return@withContext false

                    val metadata = ExportedSongMetadata.from(songId, song, fallbackTitle, context)
                    val fileName =
                        buildExportFileName(
                            metadata = metadata,
                            mimeType = format?.mimeType,
                        )
                    if (
                        exportToSelectedFolder(
                            songId = songId,
                            fileName = fileName,
                            mimeType = format?.mimeType,
                            metadata = metadata,
                            format = format,
                            spans = cachedSpans,
                        )
                    ) {
                        return@withContext true
                    }

                    val targetDirectory = StorageLocationRepository.exportedDownloadsDirectory(context)
                    if (!targetDirectory.ensureWritableDirectory()) return@withContext false

                    val targetFile = targetDirectory.resolve(fileName)
                    deleteExistingExports(
                        directory = targetDirectory,
                        songId = songId,
                        expectedBaseName = fileName.substringBeforeLast('.'),
                        except = targetFile,
                    )
                    val copied =
                        copyCachedSpans(
                            spans = cachedSpans,
                            targetFile = targetFile,
                        )
                    if (!copied) return@withContext false

                    writeEmbeddedMetadata(
                        audioFile = targetFile,
                        metadata = metadata,
                        format = format,
                    )
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf(exportMimeType(format?.mimeType)),
                        null,
                    )
                    true
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(LogTag).w(throwable, "Failed to export downloaded song %s", songId)
                    false
                } finally {
                    markExportInactive(songId)
                }
            }

        suspend fun remove(songId: String): Boolean =
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
                    expectedBaseName = metadata?.let(::buildExportBaseName),
                    except = null,
                )
            }

        private fun markExportActive(songId: String): Boolean =
            synchronized(activeExports) {
                activeExports.add(songId)
            }

        private fun markExportInactive(songId: String) {
            synchronized(activeExports) {
                activeExports.remove(songId)
            }
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

        private suspend fun selectedTreeUri(): Uri? =
            context
                .dataStore
                .data
                .first()[DownloadedSongsFolderTreeUriKey]
                ?.takeIf(String::isNotBlank)
                ?.toUri()

        private suspend fun exportToSelectedFolder(
            songId: String,
            fileName: String,
            mimeType: String?,
            metadata: ExportedSongMetadata,
            format: FormatEntity?,
            spans: List<CacheSpan>,
        ): Boolean {
            val treeUri = selectedTreeUri() ?: return false
            return runCatching {
                deleteExistingTreeExports(
                    treeUri = treeUri,
                    songId = songId,
                    expectedBaseName = fileName.substringBeforeLast('.'),
                )
                val resolver = context.contentResolver
                val parentUri = treeUri.toDocumentUri()
                val tempFile =
                    File.createTempFile(
                        "archivetune-export-",
                        ".${exportFileExtension(mimeType)}",
                        context.cacheDir,
                    )
                val copied =
                    try {
                        copyCachedSpans(
                            spans = spans,
                            targetFile = tempFile,
                        ) &&
                            writeEmbeddedMetadata(
                                audioFile = tempFile,
                                metadata = metadata,
                                format = format,
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
                        exportMimeType(mimeType),
                        fileName,
                    ) ?: run {
                        tempFile.delete()
                        return@runCatching false
                    }
                val written =
                    resolver.openOutputStream(audioUri, "w")?.use { outputStream ->
                        tempFile.inputStream().use { inputStream -> inputStream.copyTo(outputStream) }
                        true
                    } ?: false
                tempFile.delete()
                if (!written) {
                    runCatching { DocumentsContract.deleteDocument(resolver, audioUri) }
                    return@runCatching false
                }
                true
            }.getOrDefault(false)
        }

        private fun copyCachedSpans(
            spans: List<CacheSpan>,
            targetFile: File,
        ): Boolean =
            runCatching {
                targetFile.parentFile?.mkdirs()
                val tempFile = targetFile.resolveSibling("${targetFile.name}.tmp-${System.currentTimeMillis()}")
                var exportedBytes = 0L
                tempFile.outputStream().use { outputStream ->
                    exportedBytes = copyCachedSpansTo(spans, outputStream)
                }
                if (targetFile.exists() && !targetFile.delete()) {
                    tempFile.delete()
                    return@runCatching false
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                targetFile.exists() && targetFile.length() == exportedBytes
            }.getOrDefault(false)

        private fun writeEmbeddedMetadata(
            audioFile: File,
            metadata: ExportedSongMetadata,
            format: FormatEntity?,
        ): Boolean =
            runCatching {
                when (exportFileExtension(format?.mimeType)) {
                    "m4a" -> audioFile.writeMp4Metadata(metadata)
                    "aac",
                    "mp3",
                    -> audioFile.writeId3Metadata(metadata, format)

                    else -> true
                }
            }.onFailure { throwable ->
                Timber.tag(LogTag).w(throwable, "Failed to embed metadata in %s", audioFile.name)
            }.getOrDefault(true)

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
                        if (!runCatching { DocumentsContract.deleteDocument(resolver, documentUri) }.getOrDefault(false)) {
                            deleted = false
                        }
                    }
                }
            return deleted
        }

        private companion object {
            const val LogTag = "DownloadedSongExporter"
            const val MetadataLoadRetryCount = 5
            const val MetadataLoadRetryDelayMillis = 200L
        }
    }

private fun buildExportFileName(
    metadata: ExportedSongMetadata,
    mimeType: String?,
): String {
    val baseName = buildExportBaseName(metadata)
    return "$baseName.${exportFileExtension(mimeType)}"
}

private fun buildExportBaseName(metadata: ExportedSongMetadata): String {
    val title = metadata.title.toFileNamePart(maxLength = 96)
    val artist = metadata.artists.joinToString(", ").toFileNamePart(maxLength = 72)
    return "$title - $artist"
}

private fun exportIdMarker(id: String): String = "[${id.toFileNamePart(maxLength = 48)}]"

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

private fun exportFileExtension(mimeType: String?): String =
    when (mimeType.normalizedMimeType()) {
        "audio/aac",
        "audio/aac-adts",
        "audio/x-aac",
        -> "aac"

        "audio/flac",
        "audio/x-flac",
        -> "flac"

        "audio/mpeg",
        "audio/x-mpeg",
        -> "mp3"

        "audio/ogg",
        "audio/opus",
        "application/ogg",
        -> "ogg"

        "audio/webm",
        "video/webm",
        -> "webm"

        "audio/wav",
        "audio/wave",
        "audio/x-wav",
        -> "wav"

        else -> "m4a"
    }

private fun exportMimeType(mimeType: String?): String =
    when (mimeType.normalizedMimeType()) {
        "video/mp4",
        "application/mp4",
        "audio/x-m4a",
        -> "audio/mp4"

        "video/webm" -> "audio/webm"
        else -> mimeType.normalizedMimeType().ifBlank { "audio/mp4" }
    }

private fun File.writeId3Metadata(
    metadata: ExportedSongMetadata,
    format: FormatEntity?,
): Boolean {
    val original = readBytes()
    val audioBytes =
        if (original.size > Id3HeaderSize && original.copyOfRange(0, 3).decodeToString() == "ID3") {
            val existingSize = original.readId3SyncSafeInt(offset = 6) + Id3HeaderSize
            original.copyOfRange(existingSize.coerceAtMost(original.size), original.size)
        } else {
            original
        }
    val tag = buildId3Tag(metadata, format)
    outputStream().use { outputStream ->
        outputStream.write(tag)
        outputStream.write(audioBytes)
    }
    return true
}

private fun File.writeMp4Metadata(metadata: ExportedSongMetadata): Boolean {
    val original = readBytes()
    val atoms = original.readMp4Atoms(start = 0, end = original.size)
    val moov = atoms.firstOrNull { atom -> atom.type == "moov" } ?: return true
    val mdat = atoms.firstOrNull { atom -> atom.type == "mdat" }
    if (moov.end > original.size || moov.headerSize >= moov.size) return true

    val oldMoov = original.copyOfRange(moov.start, moov.end)
    val oldPayload = oldMoov.copyOfRange(moov.headerSize, oldMoov.size)
    val newPayload =
        ByteArrayOutputStream().use { outputStream ->
            outputStream.write(oldPayload)
            outputStream.write(buildMp4UdtaAtom(metadata))
            outputStream.toByteArray()
        }
    var newMoov = buildMp4Atom("moov".toByteArray(), newPayload)
    if (mdat != null && moov.start < mdat.start) {
        val delta = newMoov.size - oldMoov.size
        if (delta > 0) {
            newMoov = newMoov.copyOf()
            newMoov.adjustMp4ChunkOffsets(delta.toLong())
        }
    }

    outputStream().use { outputStream ->
        outputStream.write(original, 0, moov.start)
        outputStream.write(newMoov)
        outputStream.write(original, moov.end, original.size - moov.end)
    }
    return true
}

private fun buildId3Tag(
    metadata: ExportedSongMetadata,
    format: FormatEntity?,
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

private fun id3TextFrame(
    id: String,
    value: String,
): ByteArray = id3Frame(id, byteArrayOf(Id3Utf8EncodingByte) + value.toByteArray(Charsets.UTF_8))

private fun id3UserTextFrame(
    description: String,
    value: String,
): ByteArray =
    id3Frame(
        id = "TXXX",
        payload =
            byteArrayOf(Id3Utf8EncodingByte) +
                description.toByteArray(Charsets.UTF_8) +
                byteArrayOf(0) +
                value.toByteArray(Charsets.UTF_8),
    )

private fun id3Frame(
    id: String,
    payload: ByteArray,
): ByteArray =
    ByteArrayOutputStream().use { outputStream ->
        outputStream.write(id.toByteArray(Charsets.ISO_8859_1))
        outputStream.write(payload.size.toId3SyncSafeBytes())
        outputStream.write(byteArrayOf(0, 0))
        outputStream.write(payload)
        outputStream.toByteArray()
    }

private fun buildMp4UdtaAtom(metadata: ExportedSongMetadata): ByteArray =
    buildMp4Atom(
        "udta".toByteArray(),
        buildMp4Atom(
            "meta".toByteArray(),
            ByteArrayOutputStream().use { outputStream ->
                outputStream.write(byteArrayOf(0, 0, 0, 0))
                outputStream.write(buildMp4HandlerAtom())
                outputStream.write(buildMp4IlstAtom(metadata))
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

private fun buildMp4IlstAtom(metadata: ExportedSongMetadata): ByteArray =
    buildMp4Atom(
        "ilst".toByteArray(),
        ByteArrayOutputStream().use { outputStream ->
            outputStream.writeMp4TextItem(Mp4TitleAtom, metadata.title)
            outputStream.writeMp4TextItem(Mp4ArtistAtom, metadata.artistText)
            metadata.album?.let { album -> outputStream.writeMp4TextItem(Mp4AlbumAtom, album) }
            metadata.downloadedAt?.let { downloadedAt -> outputStream.writeMp4TextItem(Mp4DayAtom, downloadedAt) }
            metadata.thumbnailUrl?.let { thumbnailUrl -> outputStream.writeMp4TextItem(Mp4UrlAtom, thumbnailUrl) }
            outputStream.writeMp4TextItem(Mp4EncoderAtom, "ArchiveTune")
            outputStream.writeMp4TextItem(Mp4CommentAtom, "ArchiveTune ID: ${metadata.id}")
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
                "data".toByteArray(),
                ByteArrayOutputStream().use { outputStream ->
                    outputStream.writeInt(1)
                    outputStream.writeInt(0)
                    outputStream.write(value.toByteArray(Charsets.UTF_8))
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

private fun ByteArray.readMp4Atoms(
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

private fun ByteArray.adjustMp4ChunkOffsets(delta: Long) {
    adjustMp4ChunkOffsets(
        start = Mp4AtomHeaderSize,
        end = size,
        delta = delta,
    )
}

private fun ByteArray.adjustMp4ChunkOffsets(
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

private fun ByteArray.adjustStcoAtom(
    atom: Mp4Atom,
    delta: Long,
) {
    val entryCountOffset = atom.start + atom.headerSize + Mp4FullBoxHeaderSize
    if (entryCountOffset + 4 > atom.end) return
    val entryCount = readUInt32(entryCountOffset).toInt()
    var offset = entryCountOffset + 4
    repeat(entryCount) {
        if (offset + 4 > atom.end) return
        writeInt(offset, (readUInt32(offset) + delta).coerceAtMost(UIntMaxValue).toInt())
        offset += 4
    }
}

private fun ByteArray.adjustCo64Atom(
    atom: Mp4Atom,
    delta: Long,
) {
    val entryCountOffset = atom.start + atom.headerSize + Mp4FullBoxHeaderSize
    if (entryCountOffset + 4 > atom.end) return
    val entryCount = readUInt32(entryCountOffset).toInt()
    var offset = entryCountOffset + 4
    repeat(entryCount) {
        if (offset + 8 > atom.end) return
        writeLong(offset, readUInt64(offset) + delta)
        offset += 8
    }
}

private fun ByteArray.readUInt32(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 24) or
        ((this[offset + 1].toLong() and 0xFF) shl 16) or
        ((this[offset + 2].toLong() and 0xFF) shl 8) or
        (this[offset + 3].toLong() and 0xFF)

private fun ByteArray.readUInt64(offset: Int): Long =
    ((this[offset].toLong() and 0xFF) shl 56) or
        ((this[offset + 1].toLong() and 0xFF) shl 48) or
        ((this[offset + 2].toLong() and 0xFF) shl 40) or
        ((this[offset + 3].toLong() and 0xFF) shl 32) or
        ((this[offset + 4].toLong() and 0xFF) shl 24) or
        ((this[offset + 5].toLong() and 0xFF) shl 16) or
        ((this[offset + 6].toLong() and 0xFF) shl 8) or
        (this[offset + 7].toLong() and 0xFF)

private fun ByteArray.writeInt(
    offset: Int,
    value: Int,
) {
    this[offset] = ((value ushr 24) and 0xFF).toByte()
    this[offset + 1] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 3] = (value and 0xFF).toByte()
}

private fun ByteArray.writeLong(
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

private data class Mp4Atom(
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
private val Mp4ContainerAtoms = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "udta", "meta")
private val Mp4TitleAtom = byteArrayOf(0xA9.toByte(), 'n'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte())
private val Mp4ArtistAtom = byteArrayOf(0xA9.toByte(), 'A'.code.toByte(), 'R'.code.toByte(), 'T'.code.toByte())
private val Mp4AlbumAtom = byteArrayOf(0xA9.toByte(), 'a'.code.toByte(), 'l'.code.toByte(), 'b'.code.toByte())
private val Mp4DayAtom = byteArrayOf(0xA9.toByte(), 'd'.code.toByte(), 'a'.code.toByte(), 'y'.code.toByte())
private val Mp4CommentAtom = byteArrayOf(0xA9.toByte(), 'c'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte())
private val Mp4EncoderAtom = byteArrayOf(0xA9.toByte(), 't'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte())
private val Mp4UrlAtom = "purl".toByteArray()
private const val Id3HeaderSize = 10
private const val Id3Utf8EncodingByte: Byte = 3
private const val Mp4AtomHeaderSize = 8
private const val Mp4ExtendedAtomHeaderSize = 16
private const val Mp4FullBoxHeaderSize = 4
private const val UIntMaxValue = 0xFFFF_FFFFL
private const val CopyBufferSizeBytes = 256 * 1024

private data class ExportedSongMetadata(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val durationSeconds: Int,
    val thumbnailUrl: String?,
    val downloadedAt: String?,
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
            )
        }
    }
}
