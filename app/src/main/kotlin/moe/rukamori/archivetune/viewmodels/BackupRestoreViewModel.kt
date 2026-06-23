/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.InternalDatabase
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.div
import moe.rukamori.archivetune.extensions.tryOrNull
import moe.rukamori.archivetune.extensions.zipInputStream
import moe.rukamori.archivetune.extensions.zipOutputStream
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.MusicService.Companion.PERSISTENT_QUEUE_FILE
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.reportException
import org.xmlpull.v1.XmlPullParser
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.PushbackReader
import java.io.Reader
import java.io.StringReader
import java.util.zip.ZipEntry
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.system.exitProcess

data class BackupRestoreProgressUi(
    val title: String,
    val step: String,
    val percent: Int,
    val indeterminate: Boolean,
)

enum class BackupCategory {
    LIBRARY,
    ACCOUNT,
    SETTINGS,
}

data class BackupValidationResult(
    val isValid: Boolean,
    val availableCategories: Set<BackupCategory>,
    val errorMessage: String?,
)

internal fun readCsvRecords(reader: Reader): Sequence<List<String>> =
    sequence {
        val pushbackReader = if (reader is PushbackReader) reader else PushbackReader(reader, 1)
        val record = ArrayList<String>(8)
        val field = StringBuilder(64)
        var inQuotes = false

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }

        suspend fun SequenceScope<List<String>>.endRecord() {
            endField()
            val anyContent = record.any { it.isNotBlank() }
            if (anyContent) {
                yield(record.toList())
            }
            record.clear()
        }

        while (true) {
            val value = pushbackReader.read()
            if (value == -1) {
                if (field.isNotEmpty() || record.isNotEmpty()) {
                    endRecord()
                }
                break
            }

            val ch = value.toChar()
            when (ch) {
                '"' -> {
                    if (inQuotes) {
                        val next = pushbackReader.read()
                        if (next == '"'.code) {
                            field.append('"')
                        } else {
                            inQuotes = false
                            if (next != -1) pushbackReader.unread(next)
                        }
                    } else {
                        if (field.isEmpty()) {
                            inQuotes = true
                        } else {
                            field.append('"')
                        }
                    }
                }

                ',' -> {
                    if (inQuotes) {
                        field.append(',')
                    } else {
                        endField()
                    }
                }

                '\n' -> {
                    if (inQuotes) {
                        field.append('\n')
                    } else {
                        endRecord()
                    }
                }

                '\r' -> {
                    if (inQuotes) {
                        field.append('\r')
                    } else {
                        val next = pushbackReader.read()
                        if (next != '\n'.code && next != -1) pushbackReader.unread(next)
                        endRecord()
                    }
                }

                else -> {
                    field.append(ch)
                }
            }
        }
    }

@HiltViewModel
class BackupRestoreViewModel
    @Inject
    constructor(
        val database: MusicDatabase,
    ) : ViewModel() {
        private val _backupRestoreProgress = MutableStateFlow<BackupRestoreProgressUi?>(null)
        val backupRestoreProgress: StateFlow<BackupRestoreProgressUi?> = _backupRestoreProgress.asStateFlow()

        private val _backupEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val backupEvent: SharedFlow<String> = _backupEvent.asSharedFlow()

        private fun emitProgress(
            title: String,
            step: String,
            percent: Int,
            indeterminate: Boolean,
        ) {
            _backupRestoreProgress.value =
                BackupRestoreProgressUi(
                    title = title,
                    step = step,
                    percent = percent.coerceIn(0, 100),
                    indeterminate = indeterminate,
                )
        }

        fun backup(
            context: Context,
            uri: Uri,
            categories: Set<BackupCategory>,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val title = context.getString(R.string.backup_in_progress)
                try {
                    val includeSettings = BackupCategory.SETTINGS in categories
                    val includeAccount = BackupCategory.ACCOUNT in categories
                    val includeLibrary = BackupCategory.LIBRARY in categories
                    val settingsExcludedKeys = if (includeAccount) emptySet() else ACCOUNT_PREF_KEYS
                    val dbFile = context.getDatabasePath(InternalDatabase.DB_NAME)
                    val dbFiles =
                        if (includeLibrary) {
                            listOf(
                                dbFile,
                                dbFile.resolveSibling("${InternalDatabase.DB_NAME}-wal"),
                                dbFile.resolveSibling("${InternalDatabase.DB_NAME}-shm"),
                                dbFile.resolveSibling("${InternalDatabase.DB_NAME}-journal"),
                            ).filter { it.exists() }
                        } else {
                            emptyList()
                        }

                    val totalUnits = (if (includeSettings) 1 else 0) + (if (includeLibrary) 1 else 0) + dbFiles.size
                    val unitSpan = 100f / totalUnits.coerceAtLeast(1)
                    var completedUnits = 0
                    var lastPercent = -1
                    var lastStep = ""

                    fun emit(
                        step: String,
                        unitFraction: Float = 0f,
                        indeterminate: Boolean = false,
                    ) {
                        val p =
                            ((completedUnits + unitFraction.coerceIn(0f, 1f)) * unitSpan)
                                .roundToInt()
                                .coerceIn(0, 100)
                        if (p != lastPercent || step != lastStep) {
                            lastPercent = p
                            lastStep = step
                            emitProgress(
                                title = title,
                                step = step,
                                percent = p,
                                indeterminate = indeterminate,
                            )
                        }
                    }

                    context.applicationContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.buffered().zipOutputStream().use { zipStream ->
                            if (includeSettings) {
                                emit(context.getString(R.string.backup_step_export_settings), indeterminate = true)
                                zipStream.putNextEntry(ZipEntry(SETTINGS_XML_FILENAME))
                                writeSettingsToXml(context, zipStream, settingsExcludedKeys)
                                zipStream.closeEntry()
                                completedUnits++
                            }

                            if (includeLibrary) {
                                emit(context.getString(R.string.backup_step_checkpoint_database), indeterminate = true)
                                database.awaitIdle()
                                database.checkpoint()
                                completedUnits++

                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                dbFiles.forEach { file ->
                                    val fileSize = file.length().coerceAtLeast(1L)
                                    var bytesCopied = 0L
                                    emit(
                                        context.getString(R.string.backup_step_copying_file, file.name),
                                        unitFraction = 0f,
                                        indeterminate = false,
                                    )
                                    zipStream.putNextEntry(ZipEntry(file.name))
                                    FileInputStream(file).use { input ->
                                        while (true) {
                                            val read = input.read(buffer)
                                            if (read <= 0) break
                                            zipStream.write(buffer, 0, read)
                                            bytesCopied += read
                                            emit(
                                                context.getString(R.string.backup_step_copying_file, file.name),
                                                unitFraction = bytesCopied.toFloat() / fileSize.toFloat(),
                                                indeterminate = false,
                                            )
                                        }
                                    }
                                    zipStream.closeEntry()
                                    completedUnits++
                                }
                            }
                        }
                    } ?: throw IllegalStateException("Failed to open output stream")

                    val msg = context.getString(R.string.backup_create_success)
                    _backupEvent.tryEmit(msg)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                } catch (exception: Exception) {
                    reportException(exception)
                    val msg = exception.message ?: context.getString(R.string.backup_create_failed)
                    _backupEvent.tryEmit(msg)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                } finally {
                    _backupRestoreProgress.value = null
                }
            }
        }

        fun restore(
            context: Context,
            uri: Uri,
            categories: Set<BackupCategory>,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val title = context.getString(R.string.restore_in_progress)
                try {
                    val includeSettings = BackupCategory.SETTINGS in categories
                    val includeAccount = BackupCategory.ACCOUNT in categories
                    val includeLibrary = BackupCategory.LIBRARY in categories
                    val settingsExcludedKeys = if (includeAccount) emptySet() else ACCOUNT_PREF_KEYS
                    emitProgress(
                        title = title,
                        step = context.getString(R.string.restore_step_verifying),
                        percent = 0,
                        indeterminate = true,
                    )

                    val entryNames = ArrayList<String>()
                    var hasDb = false
                    context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.zipInputStream().use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                entryNames.add(entry.name)
                                if (entry.name == InternalDatabase.DB_NAME) hasDb = true
                                entry = zip.nextEntry
                            }
                        }
                    }
                    if (includeLibrary && !hasDb) throw IllegalStateException("Backup missing database")

                    val restoreEntries =
                        entryNames.filter { name ->
                            (includeSettings && (name == SETTINGS_XML_FILENAME || name == SETTINGS_FILENAME)) ||
                                (
                                    includeLibrary && (
                                        name == InternalDatabase.DB_NAME ||
                                            name == "${InternalDatabase.DB_NAME}-wal" ||
                                            name == "${InternalDatabase.DB_NAME}-shm" ||
                                            name == "${InternalDatabase.DB_NAME}-journal"
                                    )
                                )
                        }

                    val totalUnits = 1 + (if (includeLibrary) 1 else 0) + restoreEntries.size
                    val unitSpan = 100f / totalUnits.coerceAtLeast(1)
                    var completedUnits = 0

                    fun emit(
                        step: String,
                        indeterminate: Boolean,
                    ) {
                        val p = (completedUnits * unitSpan).roundToInt().coerceIn(0, 100)
                        emitProgress(title = title, step = step, percent = p, indeterminate = indeterminate)
                    }

                    completedUnits++
                    if (includeLibrary) {
                        emit(context.getString(R.string.restore_step_stopping_playback), indeterminate = true)
                        runCatching { context.stopService(Intent(context, MusicService::class.java)) }
                        runCatching { database.awaitIdle() }
                        runCatching { database.checkpoint() }
                        runCatching { database.close() }
                        completedUnits++
                    }

                    context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.zipInputStream().use { zip ->
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val name = entry.name
                                if (name !in restoreEntries) {
                                    entry = zip.nextEntry
                                    continue
                                }
                                when (name) {
                                    SETTINGS_XML_FILENAME -> {
                                        emit(context.getString(R.string.restore_step_restoring_settings), indeterminate = true)
                                        restoreSettingsFromXml(context, zip, settingsExcludedKeys)
                                    }

                                    SETTINGS_FILENAME -> {
                                        emit(context.getString(R.string.restore_step_restoring_settings), indeterminate = true)
                                        val settingsDir = context.filesDir / "datastore"
                                        if (!settingsDir.exists()) settingsDir.mkdirs()
                                        (settingsDir / SETTINGS_FILENAME).outputStream().use { out ->
                                            zip.copyTo(out)
                                        }
                                    }

                                    InternalDatabase.DB_NAME,
                                    "${InternalDatabase.DB_NAME}-wal",
                                    "${InternalDatabase.DB_NAME}-shm",
                                    "${InternalDatabase.DB_NAME}-journal",
                                    -> {
                                        emit(context.getString(R.string.restore_step_restoring_file, name), indeterminate = true)
                                        val dbFile = context.getDatabasePath(name)
                                        if (dbFile.exists()) {
                                            dbFile.delete()
                                        }
                                        FileOutputStream(dbFile).use { out ->
                                            zip.copyTo(out)
                                        }
                                    }
                                }
                                completedUnits++
                                entry = zip.nextEntry
                            }
                        }
                    }

                    emitProgress(
                        title = title,
                        step = context.getString(R.string.restore_step_restarting),
                        percent = 100,
                        indeterminate = true,
                    )

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show()
                    }

                    try {
                        context.filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
                    } catch (_: Exception) {
                    }

                    _backupRestoreProgress.value = null
                    context.startActivity(Intent(context, MainActivity::class.java))
                    exitProcess(0)
                } catch (e: Exception) {
                    reportException(e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, e.message ?: context.getString(R.string.restore_failed), Toast.LENGTH_LONG).show()
                    }
                } finally {
                    _backupRestoreProgress.value = null
                }
            }
        }

        private suspend fun writeSettingsToXml(
            context: Context,
            outputStream: java.io.OutputStream,
            excludedKeyNames: Set<String> = emptySet(),
        ) {
            val prefs =
                context.dataStore.data
                    .first()
                    .asMap()
            val serializer = android.util.Xml.newSerializer()
            serializer.setOutput(outputStream, "UTF-8")
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, "ArchiveTuneBackup")
            serializer.startTag(null, "Settings")

            for ((key, value) in prefs) {
                if (key.name in excludedKeyNames) continue
                val tagName =
                    when (value) {
                        is Boolean -> "boolean"
                        is Int -> "int"
                        is Long -> "long"
                        is Float -> "float"
                        is String -> "string"
                        is Set<*> -> "string-set"
                        else -> null
                    }
                if (tagName != null) {
                    serializer.startTag(null, tagName)
                    serializer.attribute(null, "name", key.name)
                    if (value is Set<*>) {
                        value.forEach { item ->
                            serializer.startTag(null, "item")
                            serializer.text(item.toString())
                            serializer.endTag(null, "item")
                        }
                    } else {
                        serializer.attribute(null, "value", value.toString())
                    }
                    serializer.endTag(null, tagName)
                }
            }

            serializer.endTag(null, "Settings")
            serializer.endTag(null, "ArchiveTuneBackup")
            serializer.endDocument()
            serializer.flush()
        }

        private suspend fun restoreSettingsFromXml(
            context: Context,
            inputStream: java.io.InputStream,
            excludedKeyNames: Set<String> = emptySet(),
        ) {
            val content = inputStream.readBytes().toString(Charsets.UTF_8)
            if (content.isBlank()) return

            val parser = android.util.Xml.newPullParser()
            parser.setInput(StringReader(content))

            var eventType = parser.eventType
            val booleans = LinkedHashMap<String, Boolean>()
            val ints = LinkedHashMap<String, Int>()
            val longs = LinkedHashMap<String, Long>()
            val floats = LinkedHashMap<String, Float>()
            val strings = LinkedHashMap<String, String>()
            val stringSets = LinkedHashMap<String, Set<String>>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val name = parser.name
                    val keyName = parser.getAttributeValue(null, "name")

                    if (keyName != null && keyName !in excludedKeyNames) {
                        when (name) {
                            "boolean" -> {
                                val value = parser.getAttributeValue(null, "value")?.toBoolean()
                                if (value != null) {
                                    booleans[keyName] = value
                                }
                            }

                            "int" -> {
                                val value = parser.getAttributeValue(null, "value")?.toIntOrNull()
                                if (value != null) {
                                    ints[keyName] = value
                                }
                            }

                            "long" -> {
                                val value = parser.getAttributeValue(null, "value")?.toLongOrNull()
                                if (value != null) {
                                    longs[keyName] = value
                                }
                            }

                            "float" -> {
                                val value = parser.getAttributeValue(null, "value")?.toFloatOrNull()
                                if (value != null) {
                                    floats[keyName] = value
                                }
                            }

                            "string" -> {
                                val value = parser.getAttributeValue(null, "value")
                                if (value != null) {
                                    strings[keyName] = value
                                }
                            }

                            "string-set" -> {
                                val values = LinkedHashSet<String>()
                                while (true) {
                                    val next = parser.next()
                                    if (next == XmlPullParser.START_TAG && parser.name == "item") {
                                        values.add(parser.nextText())
                                        continue
                                    }
                                    if (next == XmlPullParser.END_TAG && parser.name == "string-set") {
                                        break
                                    }
                                    if (next == XmlPullParser.END_DOCUMENT) {
                                        break
                                    }
                                }
                                stringSets[keyName] = values
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (
                booleans.isEmpty() &&
                ints.isEmpty() &&
                longs.isEmpty() &&
                floats.isEmpty() &&
                strings.isEmpty() &&
                stringSets.isEmpty()
            ) {
                return
            }

            context.dataStore.edit { prefs ->
                booleans.forEach { (k, v) -> prefs[booleanPreferencesKey(k)] = v }
                ints.forEach { (k, v) -> prefs[intPreferencesKey(k)] = v }
                longs.forEach { (k, v) -> prefs[longPreferencesKey(k)] = v }
                floats.forEach { (k, v) -> prefs[floatPreferencesKey(k)] = v }
                strings.forEach { (k, v) -> prefs[stringPreferencesKey(k)] = v }
                stringSets.forEach { (k, v) -> prefs[stringSetPreferencesKey(k)] = v }
            }
        }

        private fun normalizeCsvHeaderCell(value: String): String =
            value
                .trim()
                .trimStart('\uFEFF')
                .lowercase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")

        suspend fun importPlaylistFromCsv(
            context: Context,
            uri: Uri,
        ): ArrayList<Song> {
            val songs =
                withContext(Dispatchers.IO) {
                    val out = arrayListOf<Song>()

                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val reader = PushbackReader(InputStreamReader(stream, Charsets.UTF_8), 1)
                            val iterator = readCsvRecords(reader).iterator()
                            if (!iterator.hasNext()) return@use

                            val firstRecord = iterator.next()
                            val normalizedHeader = firstRecord.map(::normalizeCsvHeaderCell)

                            val titleIndex =
                                normalizedHeader.indexOfFirst { it == "title" || it == "tracktitle" || it == "songtitle" }
                            val artistIndex =
                                normalizedHeader.indexOfFirst { it == "artist" || it == "artists" || it == "artistname" }

                            val hasHeader = titleIndex >= 0 && artistIndex >= 0
                            val resolvedTitleIndex = if (hasHeader) titleIndex else 0
                            val resolvedArtistIndex = if (hasHeader) artistIndex else 1

                            fun addFromRecord(record: List<String>) {
                                val titleRaw = record.getOrNull(resolvedTitleIndex).orEmpty()
                                val artistRaw = record.getOrNull(resolvedArtistIndex).orEmpty()

                                val title = titleRaw.trim().trimStart('\uFEFF')
                                if (title.isBlank()) return

                                val artistStr = artistRaw.trim()
                                val artists =
                                    artistStr
                                        .split(';', '|')
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .map { ArtistEntity(id = "", name = it) }

                                out.add(
                                    Song(
                                        song = SongEntity(id = "", title = title),
                                        artists = if (artists.isEmpty()) listOf(ArtistEntity("", "")) else artists,
                                    ),
                                )
                            }

                            if (!hasHeader) {
                                addFromRecord(firstRecord)
                            }
                            while (iterator.hasNext()) {
                                addFromRecord(iterator.next())
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }

                    out
                }

            if (songs.isEmpty()) {
                Toast
                    .makeText(
                        context,
                        "No songs found. Invalid file, or perhaps no song matches were found.",
                        Toast.LENGTH_SHORT,
                    ).show()
            }

            return songs
        }

        suspend fun loadM3UOnline(
            context: Context,
            uri: Uri,
        ): ArrayList<Song> {
            val songs =
                withContext(Dispatchers.IO) {
                    val out = ArrayList<Song>()

                    runCatching {
                        context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
                            val lines = stream.bufferedReader().readLines()
                            if (lines.firstOrNull()?.startsWith("#EXTM3U") == true) {
                                lines.forEach { rawLine ->
                                    if (rawLine.startsWith("#EXTINF:")) {
                                        val artists =
                                            rawLine
                                                .substringAfter("#EXTINF:")
                                                .substringAfter(',')
                                                .substringBefore(" - ")
                                                .split(';')
                                        val title =
                                            rawLine
                                                .substringAfter("#EXTINF:")
                                                .substringAfter(',')
                                                .substringAfter(" - ")

                                        out.add(
                                            Song(
                                                song = SongEntity(id = "", title = title),
                                                artists = artists.map { ArtistEntity("", it) },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }

                    out
                }

            if (songs.isEmpty()) {
                Toast
                    .makeText(
                        context,
                        "No songs found. Invalid file, or perhaps no song matches were found.",
                        Toast.LENGTH_SHORT,
                    ).show()
            }

            return songs
        }

        suspend fun validateBackup(
            context: Context,
            uri: Uri,
        ): BackupValidationResult = withContext(Dispatchers.IO) {
            try {
                val stream =
                    context.applicationContext.contentResolver.openInputStream(uri)
                if (stream == null) {
                    return@withContext BackupValidationResult(
                        isValid = false,
                        availableCategories = emptySet(),
                        errorMessage = context.getString(R.string.restore_file_not_found),
                    )
                }
                stream.use { inputStream ->
                    val zipStream = inputStream.zipInputStream()
                    val entryNames = mutableSetOf<String>()
                    zipStream.use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            entryNames.add(entry.name)
                            entry = zip.nextEntry
                        }
                    }
                    if (entryNames.isEmpty()) {
                        return@withContext BackupValidationResult(
                            isValid = false,
                            availableCategories = emptySet(),
                            errorMessage = context.getString(R.string.restore_invalid_file),
                        )
                    }
                    val categories = mutableSetOf<BackupCategory>()
                    val hasSettings = SETTINGS_XML_FILENAME in entryNames || SETTINGS_FILENAME in entryNames
                    val hasDb = entryNames.any { it.startsWith(InternalDatabase.DB_NAME) }
                    if (hasSettings) {
                        categories.add(BackupCategory.SETTINGS)
                        categories.add(BackupCategory.ACCOUNT)
                    }
                    if (hasDb) {
                        categories.add(BackupCategory.LIBRARY)
                    }
                    if (categories.isEmpty()) {
                        return@withContext BackupValidationResult(
                            isValid = false,
                            availableCategories = emptySet(),
                            errorMessage = context.getString(R.string.restore_missing_content),
                        )
                    }
                    BackupValidationResult(
                        isValid = true,
                        availableCategories = categories,
                        errorMessage = null,
                    )
                }
            } catch (e: Exception) {
                reportException(e)
                BackupValidationResult(
                    isValid = false,
                    availableCategories = emptySet(),
                    errorMessage = context.getString(R.string.restore_corrupted),
                )
            }
        }

        companion object {
            const val SETTINGS_FILENAME = "settings.preferences_pb"
            const val SETTINGS_XML_FILENAME = "settings.xml"

            val ACCOUNT_PREF_KEYS: Set<String> =
                setOf(
                    "innerTubeCookie",
                    "visitorData",
                    "dataSyncId",
                    "poToken",
                    "poTokenGvs",
                    "poTokenPlayer",
                    "poTokenSourceUrl",
                    "webClientPoTokenEnabled",
                    "accountName",
                    "accountEmail",
                    "accountChannelHandle",
                    "useLoginForBrowse",
                    "lastfmSession",
                    "lastfmUsername",
                    "lastfmProvider",
                    "lastfmCustomEndpoint",
                    "lastfmApiKeyOverride",
                    "lastfmSecretOverride",
                    "listenbrainz_token",
                    "discordToken",
                    "discordUsername",
                    "discordName",
                    "proxyUsername",
                    "proxyPassword",
                    "spotify_sp_dc",
                    "spotify_sp_key",
                    "spotify_access_token",
                    "spotify_access_token_expires_at",
                    "spotify_account_name",
                    "spotify_account_avatar_url",
                )
        }
    }
