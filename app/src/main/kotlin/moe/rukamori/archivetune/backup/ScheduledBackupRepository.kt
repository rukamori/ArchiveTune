/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.rukamori.archivetune.utils.dataStore
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledBackupRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val updateMutex = Mutex()

        fun observeSettings(): Flow<ScheduledBackupSettings?> =
            context.dataStore.data.map { preferences ->
                val hasConfiguration =
                    preferences.contains(ENABLED_KEY) ||
                        preferences.contains(FREQUENCY_KEY) ||
                        preferences.contains(DIRECTORY_URI_KEY) ||
                        preferences.contains(OVERWRITE_KEY)
                if (!hasConfiguration) return@map null
                preferences.toScheduledBackupSettings()
            }

        suspend fun getSettings(): ScheduledBackupSettings? =
            context.dataStore.data.first().let { preferences ->
                val hasConfiguration =
                    preferences.contains(ENABLED_KEY) ||
                        preferences.contains(FREQUENCY_KEY) ||
                        preferences.contains(DIRECTORY_URI_KEY) ||
                        preferences.contains(OVERWRITE_KEY)
                if (hasConfiguration) preferences.toScheduledBackupSettings() else null
            }

        suspend fun updateEnabled(enabled: Boolean): ScheduledBackupSettings =
            updateMutex.withLock {
                context.dataStore
                    .edit { preferences ->
                        preferences[ENABLED_KEY] = enabled
                    }.toScheduledBackupSettings()
            }

        suspend fun updateFrequency(frequency: ScheduledBackupFrequency): ScheduledBackupSettings =
            updateMutex.withLock {
                context.dataStore
                    .edit { preferences ->
                        preferences[FREQUENCY_KEY] = frequency.name
                    }.toScheduledBackupSettings()
            }

        suspend fun updateCustomDate(epochDay: Long): ScheduledBackupSettings =
            updateMutex.withLock {
                require(epochDay >= LocalDate.now().toEpochDay()) { "Backup date cannot be in the past" }
                context.dataStore
                    .edit { preferences ->
                        preferences[FREQUENCY_KEY] = ScheduledBackupFrequency.CUSTOM.name
                        preferences[CUSTOM_DATE_KEY] = epochDay
                    }.toScheduledBackupSettings()
            }

        suspend fun updateOverwrite(overwriteExisting: Boolean): ScheduledBackupSettings =
            updateMutex.withLock {
                context.dataStore
                    .edit { preferences ->
                        preferences[OVERWRITE_KEY] = overwriteExisting
                    }.toScheduledBackupSettings()
            }

        suspend fun updateDirectory(uri: Uri): ScheduledBackupSettings =
            updateMutex.withLock {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                val directoryName = resolveDirectoryName(uri)
                context.dataStore
                    .edit { preferences ->
                        preferences[DIRECTORY_URI_KEY] = uri.toString()
                        preferences[DIRECTORY_NAME_KEY] = directoryName
                    }.toScheduledBackupSettings()
            }

        private fun resolveDirectoryName(uri: Uri): String {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            return context.contentResolver
                .query(
                    documentUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor -> cursor.readDisplayName() }
                ?.takeIf(String::isNotBlank)
                ?: documentId.substringAfterLast(':').ifBlank { documentId }
        }

        private fun Cursor.readDisplayName(): String? =
            if (moveToFirst()) {
                getString(getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
            } else {
                null
            }

        companion object {
            private val ENABLED_KEY = booleanPreferencesKey("scheduledBackupEnabled")
            private val FREQUENCY_KEY = stringPreferencesKey("scheduledBackupFrequency")
            private val CUSTOM_DATE_KEY = longPreferencesKey("scheduledBackupCustomDateEpochDay")
            private val DIRECTORY_URI_KEY = stringPreferencesKey("scheduledBackupDirectoryUri")
            private val DIRECTORY_NAME_KEY = stringPreferencesKey("scheduledBackupDirectoryName")
            private val OVERWRITE_KEY = booleanPreferencesKey("scheduledBackupOverwriteExisting")

            val NON_PORTABLE_PREFERENCE_KEYS: Set<String> =
                setOf(
                    ENABLED_KEY.name,
                    DIRECTORY_URI_KEY.name,
                    DIRECTORY_NAME_KEY.name,
                )

            private fun androidx.datastore.preferences.core.Preferences.toScheduledBackupSettings(): ScheduledBackupSettings =
                ScheduledBackupSettings(
                    enabled = this[ENABLED_KEY] ?: false,
                    frequency =
                        this[FREQUENCY_KEY]
                            ?.let { stored -> ScheduledBackupFrequency.entries.firstOrNull { it.name == stored } }
                            ?: ScheduledBackupFrequency.WEEKLY,
                    customDateEpochDay = this[CUSTOM_DATE_KEY],
                    directoryUri = this[DIRECTORY_URI_KEY]?.let(Uri::parse),
                    directoryName = this[DIRECTORY_NAME_KEY],
                    overwriteExisting = this[OVERWRITE_KEY] ?: false,
                )
        }
    }
