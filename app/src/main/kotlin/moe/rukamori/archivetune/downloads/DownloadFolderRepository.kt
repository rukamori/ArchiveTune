/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.Download
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.DownloadFolderDisplayNameKey
import moe.rukamori.archivetune.constants.DownloadFolderTreeUriKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadFolderSelection(
    val treeUri: String?,
    val displayName: String?,
)

class ObserveDownloadFolderUseCase
    @Inject
    constructor(
        private val repository: DownloadFolderRepository,
    ) {
        operator fun invoke(): Flow<DownloadFolderSelection> = repository.selection
    }

class SetDownloadFolderUseCase
    @Inject
    constructor(
        private val repository: DownloadFolderRepository,
    ) {
        suspend operator fun invoke(uri: Uri) = repository.setFolder(uri)
    }

class ExportCompletedDownloadUseCase
    @Inject
    constructor(
        private val repository: DownloadFolderRepository,
    ) {
        suspend operator fun invoke(download: Download) = repository.exportCompletedDownload(download)
    }

@Singleton
class DownloadFolderRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @DownloadCache private val downloadCache: Cache,
        private val database: MusicDatabase,
    ) {
        val selection: Flow<DownloadFolderSelection> =
            context.dataStore.data.map { preferences ->
                DownloadFolderSelection(
                    treeUri = preferences[DownloadFolderTreeUriKey],
                    displayName = preferences[DownloadFolderDisplayNameKey],
                )
            }

        suspend fun setFolder(uri: Uri) =
            withContext(Dispatchers.IO) {
                if (uri.scheme != "content") throw IOException("Download folder must be a document provider URI")
                val previousUri = context.dataStore.data.first()[DownloadFolderTreeUriKey]
                val permissions =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, permissions)
                try {
                    val displayName = queryDisplayName(uri)
                    context.dataStore.edit { preferences ->
                        preferences[DownloadFolderTreeUriKey] = uri.toString()
                        preferences[DownloadFolderDisplayNameKey] = displayName
                    }
                } catch (exception: Exception) {
                    if (previousUri != uri.toString()) {
                        try {
                            context.contentResolver.releasePersistableUriPermission(uri, permissions)
                        } catch (releaseException: SecurityException) {
                            exception.addSuppressed(releaseException)
                        }
                    }
                    throw exception
                }
                if (!previousUri.isNullOrBlank() && previousUri != uri.toString()) {
                    try {
                        context.contentResolver.releasePersistableUriPermission(previousUri.toUri(), permissions)
                    } catch (exception: SecurityException) {
                        Timber.w(exception, "Unable to release the previous download folder permission")
                    }
                }
            }

        suspend fun exportCompletedDownload(download: Download) =
            withContext(Dispatchers.IO) {
                if (download.state != Download.STATE_COMPLETED) return@withContext
                val treeUri =
                    context.dataStore.data.first()[DownloadFolderTreeUriKey]
                        ?.toUri()
                        ?: return@withContext
                val song = database.getSongByIdBlocking(download.request.id)
                val mimeType = song?.format?.mimeType?.substringBefore(';') ?: "application/octet-stream"
                val extension =
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                        ?: "audio"
                val title = song?.song?.title?.takeIf(String::isNotBlank) ?: download.request.id
                val artist = song?.artists?.joinToString(", ") { it.name }?.takeIf(String::isNotBlank)
                val fileName =
                    buildString {
                        append(title)
                        if (artist != null) append(" - ").append(artist)
                        append(" [").append(download.request.id).append("]")
                    }.toSafeFileName(MAX_DOWNLOAD_FILE_NAME_LENGTH - extension.length - 1) + ".$extension"

                val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
                val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
                if (findChildDocument(treeUri, rootDocumentId, fileName) != null) return@withContext
                val outputUri =
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        rootDocumentUri,
                        mimeType,
                        fileName,
                    ) ?: throw IOException("The selected download folder rejected file creation")

                try {
                    val output =
                        context.contentResolver.openOutputStream(outputUri, "wt")
                            ?: throw IOException("Unable to open the downloaded audio file for writing")
                    val source =
                        CacheDataSource.Factory()
                            .setCache(downloadCache)
                            .setUpstreamDataSourceFactory(FileDataSource.Factory())
                            .setCacheWriteDataSinkFactory(null)
                            .createDataSource()
                    output.use { outputStream ->
                        try {
                            source.open(
                                DataSpec
                                    .Builder()
                                    .setUri(download.request.uri)
                                    .setKey(download.request.id)
                                    .setLength(download.contentLength.takeIf { length -> length > 0L } ?: C.LENGTH_UNSET.toLong())
                                    .build(),
                            )
                            val buffer = ByteArray(EXPORT_BUFFER_SIZE)
                            while (true) {
                                val bytesRead = source.read(buffer, 0, buffer.size)
                                if (bytesRead == C.RESULT_END_OF_INPUT) break
                                outputStream.write(buffer, 0, bytesRead)
                            }
                        } finally {
                            source.close()
                        }
                    }
                } catch (exception: Exception) {
                    try {
                        DocumentsContract.deleteDocument(context.contentResolver, outputUri)
                    } catch (cleanupException: Exception) {
                        exception.addSuppressed(cleanupException)
                    }
                    throw exception
                }
            }

        private fun queryDisplayName(uri: Uri): String {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            context.contentResolver.query(
                documentUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
            throw IOException("Unable to read the selected download folder name")
        }

        private fun findChildDocument(
            treeUri: Uri,
            rootDocumentId: String,
            displayName: String,
        ): Uri? {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == displayName) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
                    }
                }
            }
            return null
        }

        private companion object {
            const val EXPORT_BUFFER_SIZE = 16 * 1024
        }
    }

private fun String.toSafeFileName(maxLength: Int): String =
    replace(Regex("[\\\\/:*?\"<>|\\p{Cc}]"), "_")
        .trim()
        .trim('.')
        .take(maxLength.coerceAtLeast(1))
        .ifBlank { "download" }

private const val MAX_DOWNLOAD_FILE_NAME_LENGTH = 180
