/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.reportException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ScheduledBackupWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val dependencies =
            EntryPointAccessors.fromApplication(
                applicationContext,
                ScheduledBackupWorkerEntryPoint::class.java,
            )
        val settings = dependencies.repository().getSettings() ?: return Result.success()
        if (!settings.enabled || settings.directoryUri == null) return Result.success()

        return try {
            val destination = createDestination(settings)
            dependencies.createBackupUseCase()(
                uri = destination,
                categories = BackupArchiveCategory.entries.toSet(),
            )
            dependencies.scheduler().appendNext(settings)
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (security: SecurityException) {
            reportException(security)
            Result.failure()
        } catch (exception: Exception) {
            reportException(exception)
            Result.retry()
        }
    }

    private fun createDestination(settings: ScheduledBackupSettings): Uri {
        val treeUri = requireNotNull(settings.directoryUri)
        val fileName =
            if (settings.overwriteExisting) {
                "${applicationContext.getString(R.string.app_name)}.backup"
            } else {
                val timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMATTER)
                "${applicationContext.getString(R.string.app_name)}_$timestamp.backup"
            }

        if (settings.overwriteExisting) {
            findChildDocument(treeUri, fileName)?.let { return it }
        }

        val parentDocumentUri =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        return DocumentsContract.createDocument(
            applicationContext.contentResolver,
            parentDocumentUri,
            BACKUP_MIME_TYPE,
            fileName,
        ) ?: throw IllegalStateException("Failed to create scheduled backup file")
    }

    private fun findChildDocument(
        treeUri: Uri,
        displayName: String,
    ): Uri? {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        return applicationContext.contentResolver
            .query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameColumn) == displayName) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idColumn))
                    }
                }
                null
            }
    }

    companion object {
        private const val BACKUP_MIME_TYPE = "application/octet-stream"
        private val FILE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScheduledBackupWorkerEntryPoint {
    fun repository(): ScheduledBackupRepository

    fun scheduler(): ScheduledBackupScheduler

    fun createBackupUseCase(): CreateBackupUseCase
}
