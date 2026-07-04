/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.logcat

import android.content.Context
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.LogEntry
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val LOGCAT_POLL_INTERVAL_MILLIS = 2_000L
private const val LOGCAT_ENTRY_LIMIT = 1_000
private const val LOGCAT_CHUNK_LENGTH = 4_000
private const val DUPLICATE_WINDOW_MILLIS = 1_000L

@Singleton
class LogcatRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val clearTimestamp = MutableStateFlow(0L)

        fun observeEntries(): Flow<List<LogEntry>> =
            combine(
                GlobalLog.logs,
                observeLogcatSnapshots(),
                clearTimestamp,
            ) { capturedEntries, logcatEntries, clearedAt ->
                mergeCapturedAndLogcatEntries(
                    capturedEntries = capturedEntries,
                    logcatEntries = logcatEntries,
                ).asSequence()
                    .filter { entry -> entry.time >= clearedAt }
                    .sortedBy(LogEntry::time)
                    .toList()
            }.flowOn(Dispatchers.Default)

        fun clear() {
            clearTimestamp.value = System.currentTimeMillis()
            GlobalLog.clear()
        }

        suspend fun exportText(content: String): Uri =
            withContext(Dispatchers.IO) {
                require(content.isNotBlank())

                val exportDirectory = File(context.cacheDir, "shared_logs")
                if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
                    throw IOException("Unable to create log export directory")
                }

                val exportFile =
                    File(
                        exportDirectory,
                        "archivetune-log-${System.currentTimeMillis()}.txt",
                    )
                exportFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.newLine()
                }

                exportDirectory
                    .listFiles { file -> file.isFile && file.extension.equals("txt", ignoreCase = true) }
                    ?.sortedByDescending(File::lastModified)
                    ?.drop(5)
                    ?.forEach { file -> file.delete() }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.FileProvider",
                    exportFile,
                )
            }

        private fun observeLogcatSnapshots(): Flow<List<LogEntry>> =
            flow {
                while (currentCoroutineContext().isActive) {
                    emit(readLogcatEntries())
                    delay(LOGCAT_POLL_INTERVAL_MILLIS)
                }
            }.flowOn(Dispatchers.IO)

        private suspend fun readLogcatEntries(): List<LogEntry> {
            val processId = Process.myPid()
            val timestampFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val linePattern =
                Regex(
                    """^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/""" +
                        """(.+?)\(\s*\d+\):\s*(.*)$""",
                )
            val logcatProcess =
                ProcessBuilder(
                    "logcat",
                    "--pid=$processId",
                    "-v",
                    "time",
                    "-t",
                    LOGCAT_ENTRY_LIMIT.toString(),
                ).redirectErrorStream(true)
                    .start()

            return try {
                val entries = ArrayList<LogEntry>(LOGCAT_ENTRY_LIMIT)
                BufferedReader(InputStreamReader(logcatProcess.inputStream)).use { reader ->
                    reader.forEachLine { line ->
                        val match = linePattern.matchEntire(line) ?: return@forEachLine
                        val (timestamp, levelCode, tag, message) = match.destructured
                        val level =
                            when (levelCode) {
                                "V" -> Log.VERBOSE
                                "D" -> Log.DEBUG
                                "I" -> Log.INFO
                                "W" -> Log.WARN
                                "E", "F" -> Log.ERROR
                                else -> return@forEachLine
                            }
                        val parsedTimestamp = timestampFormat.parse(timestamp) ?: return@forEachLine
                        val calendar =
                            Calendar.getInstance().apply {
                                time = parsedTimestamp
                                set(Calendar.YEAR, currentYear)
                            }
                        entries +=
                            LogEntry(
                                time = calendar.timeInMillis,
                                level = level,
                                tag = tag.trim(),
                                message = message,
                            )
                    }
                }

                val exitCode = logcatProcess.waitFor()
                currentCoroutineContext().ensureActive()
                if (exitCode != 0) {
                    throw IOException("logcat exited with code $exitCode")
                }
                entries
            } finally {
                logcatProcess.destroy()
            }
        }
    }

private fun mergeCapturedAndLogcatEntries(
    capturedEntries: List<LogEntry>,
    logcatEntries: List<LogEntry>,
): List<LogEntry> {
    val consumedLogcatEntries = BooleanArray(logcatEntries.size)

    capturedEntries.forEach { capturedEntry ->
        capturedEntry.message.toLogcatChunks().forEach { chunk ->
            val matchingIndex =
                logcatEntries.indices.firstOrNull { index ->
                    if (consumedLogcatEntries[index]) {
                        return@firstOrNull false
                    }

                    val logcatEntry = logcatEntries[index]
                    logcatEntry.level == capturedEntry.level &&
                        (capturedEntry.tag == null || logcatEntry.tag == capturedEntry.tag) &&
                        logcatEntry.message == chunk &&
                        abs(logcatEntry.time - capturedEntry.time) <= DUPLICATE_WINDOW_MILLIS
                }
            if (matchingIndex != null) {
                consumedLogcatEntries[matchingIndex] = true
            }
        }
    }

    val uniqueLogcatEntries =
        logcatEntries.filterIndexed { index, _ ->
            !consumedLogcatEntries[index]
        }

    return capturedEntries + uniqueLogcatEntries.mergeContinuationChunks()
}

private fun String.toLogcatChunks(): List<String> {
    if (length < LOGCAT_CHUNK_LENGTH) {
        return listOf(this)
    }

    val chunks = mutableListOf<String>()
    var start = 0
    while (start < length) {
        val newlineIndex = indexOf('\n', start).let { index -> if (index == -1) length else index }
        do {
            val end = minOf(newlineIndex, start + LOGCAT_CHUNK_LENGTH)
            chunks += substring(start, end)
            start = end
        } while (start < newlineIndex)
        start++
    }
    return chunks
}

private fun List<LogEntry>.mergeContinuationChunks(): List<LogEntry> {
    val mergedEntries = ArrayList<LogEntry>(size)
    sortedBy(LogEntry::time).forEach { entry ->
        val previous = mergedEntries.lastOrNull()
        if (
            previous != null &&
            previous.message.length >= LOGCAT_CHUNK_LENGTH &&
            previous.level == entry.level &&
            previous.tag == entry.tag &&
            entry.time - previous.time in 0..DUPLICATE_WINDOW_MILLIS
        ) {
            mergedEntries[mergedEntries.lastIndex] =
                previous.copy(message = previous.message + entry.message)
        } else {
            mergedEntries += entry
        }
    }
    return mergedEntries
}
