/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.LocalSongsAutoScanKey
import moe.rukamori.archivetune.constants.LocalSongsExcludedFoldersKey
import moe.rukamori.archivetune.constants.LocalSongsIncludedFoldersKey
import moe.rukamori.archivetune.constants.LocalSongsMinDurationSecondsKey
import moe.rukamori.archivetune.utils.dataStore

class LocalMusicFoldersRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val configuration: Flow<LocalSongScanConfig> = context.dataStore.data.map(::readConfiguration).distinctUntilChanged()

    suspend fun currentConfiguration(): LocalSongScanConfig = configuration.first()

    suspend fun readAccess(): LocalMusicAccess = withContext(Dispatchers.IO) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else Manifest.permission.READ_EXTERNAL_STORAGE
        LocalMusicAccess(
            hasMediaPermission = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
            readableFolders = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }
                .map { it.uri.toString() }.toSet(),
        )
    }

    suspend fun setMinimumDuration(seconds: Int): LocalSongScanConfig =
        readConfiguration(context.dataStore.edit { it[LocalSongsMinDurationSecondsKey] = seconds })

    suspend fun setAutoScan(enabled: Boolean): LocalSongScanConfig =
        readConfiguration(context.dataStore.edit { it[LocalSongsAutoScanKey] = enabled })

    suspend fun addExcludedFolder(uri: Uri): LocalSongScanConfig = withContext(Dispatchers.IO) {
        require(DocumentsContract.isTreeUri(uri))
        readConfiguration(context.dataStore.edit { preferences ->
            preferences[LocalSongsExcludedFoldersKey] = LocalSongScanConfig.deduplicateFolderEntries(
                preferences[LocalSongsExcludedFoldersKey].orEmpty() + uri.toString(),
            )
            preferences[LocalSongsIncludedFoldersKey] = preferences[LocalSongsIncludedFoldersKey].orEmpty()
                .filterNot { LocalSongScanConfig.sameFolder(it, uri.toString()) }.toSet()
        })
    }

    suspend fun removeFolder(entry: String, included: Boolean): LocalSongScanConfig =
        readConfiguration(context.dataStore.edit { preferences ->
            val key = if (included) LocalSongsIncludedFoldersKey else LocalSongsExcludedFoldersKey
            preferences[key] = preferences[key].orEmpty().filterNot { it == entry }.toSet()
        })

    suspend fun addIncludedFolder(uri: Uri): LocalSongScanConfig = withContext(Dispatchers.IO) {
        require(DocumentsContract.isTreeUri(uri))
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val selected = LocalMusicFolder.fromEntry(uri.toString())
        val preferences = context.dataStore.edit { preferences ->
            val included = preferences[LocalSongsIncludedFoldersKey].orEmpty().filterNot {
                val folder = LocalMusicFolder.fromEntry(it)
                folder.path.equals(selected.path, ignoreCase = true) &&
                    (folder.volume == null || folder.volume.equals(selected.volume, ignoreCase = true))
            }
            preferences[LocalSongsIncludedFoldersKey] =
                LocalSongScanConfig.deduplicateFolderEntries(included + uri.toString())
            preferences[LocalSongsExcludedFoldersKey] = preferences[LocalSongsExcludedFoldersKey].orEmpty().filterNot {
                LocalMusicFolder.fromEntry(it).contains(selected)
            }.toSet()
        }
        readConfiguration(preferences)
    }

    private fun readConfiguration(preferences: Preferences): LocalSongScanConfig = LocalSongScanConfig(
        minimumDurationSeconds = preferences[LocalSongsMinDurationSecondsKey] ?: 0,
        includedFolders = preferences[LocalSongsIncludedFoldersKey].orEmpty(),
        excludedFolders = preferences[LocalSongsExcludedFoldersKey].orEmpty(),
        autoScanEnabled = preferences[LocalSongsAutoScanKey] ?: true,
    )

    internal suspend fun audioDocuments(
        treeUri: Uri,
        excludedFolders: List<LocalMusicFolder>,
    ): List<LocalAudioDocument> = withContext(Dispatchers.IO) {
        val documents = mutableListOf<LocalAudioDocument>()
        val directories = ArrayDeque<String>()
        val visited = hashSetOf<String>()
        directories.add(DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (directories.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directoryId = directories.removeLast()
            if (!visited.add(directoryId)) continue
            val folder = LocalMusicFolder.fromDocumentId(treeUri, directoryId)
            if (excludedFolders.any { it.contains(folder) }) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
            val cursor = context.contentResolver.query(children, projection, null, null, null)
                ?: throw IOException("Document provider returned no folder cursor")
            cursor.use {
                if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING)) {
                    throw IOException("Document provider has not finished loading the folder")
                }
                it.extras.getString(DocumentsContract.EXTRA_ERROR)?.let { message -> throw IOException(message) }
                val idIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = it.getString(idIndex)
                    val mimeType = it.getString(mimeIndex)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        directories.add(id)
                        continue
                    }
                    val name = it.getString(nameIndex)
                    if (!SupportedLocalAudio.isSupported(name, mimeType)) continue
                    val size = if (it.isNull(sizeIndex)) null else it.getLong(sizeIndex)
                    if (size == 0L) continue
                    documents += LocalAudioDocument(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                        displayName = name,
                        mimeType = mimeType ?: "audio/*",
                        sizeBytes = size?.coerceAtLeast(0L) ?: 0L,
                        dateModifiedSeconds = if (it.isNull(modifiedIndex)) 0L else it.getLong(modifiedIndex) / 1_000L,
                    )
                }
            }
        }
        documents
    }
}

data class LocalMusicAccess(
    val hasMediaPermission: Boolean,
    val readableFolders: Set<String>,
)

internal data class LocalAudioDocument(
    val uri: Uri,
    val displayName: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
)
