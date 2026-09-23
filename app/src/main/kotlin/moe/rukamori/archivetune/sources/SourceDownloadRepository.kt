package moe.rukamori.archivetune.sources

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SourceDownloadRecord(val version: Int = 1, val selection: SourceSelection, val complete: Boolean = false)

@Singleton
class SourceDownloadRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cipher = SourceSecretCipher()

    @Synchronized
    fun read(mediaId: String): SourceDownloadRecord? {
        val file = file(mediaId)
        if (!file.baseFile.exists()) return null
        return json.decodeFromString<SourceDownloadRecord>(cipher.decrypt(file.openRead().bufferedReader().use { it.readText() }))
    }

    @Synchronized
    fun write(mediaId: String, record: SourceDownloadRecord) {
        val file = file(mediaId)
        val output = file.startWrite()
        try {
            output.write(cipher.encrypt(json.encodeToString(SourceDownloadRecord.serializer(), record)).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (failure: Throwable) {
            file.failWrite(output)
            throw failure
        }
    }

    @Synchronized
    fun remove(mediaId: String) = file(mediaId).delete()

    private fun file(mediaId: String): AtomicFile {
        val directory = File(context.noBackupFilesDir, "source-downloads")
        if (!directory.isDirectory && !directory.mkdirs()) throw SourceException(SourceProblem.STORAGE)
        return AtomicFile(File(directory, mediaId.sourceHash() + ".json"))
    }
}
