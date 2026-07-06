/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.logcat

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class LogcatLevel(
    val priority: Int,
    val label: String,
) {
    VERBOSE(Log.VERBOSE, "V"),
    DEBUG(Log.DEBUG, "D"),
    INFO(Log.INFO, "I"),
    WARNING(Log.WARN, "W"),
    ERROR(Log.ERROR, "E"),
}

data class LogcatRecord(
    val id: String,
    val time: Long,
    val level: LogcatLevel,
    val tag: String?,
    val message: String,
)

class ObserveLogcatUseCase
    @Inject
    constructor(
        private val repository: LogcatRepository,
    ) {
        operator fun invoke(): Flow<List<LogcatRecord>> =
            repository.observeEntries().map { entries ->
                val occurrences = mutableMapOf<String, Int>()
                entries.map { entry ->
                    val level =
                        when (entry.level) {
                            Log.VERBOSE -> LogcatLevel.VERBOSE
                            Log.DEBUG -> LogcatLevel.DEBUG
                            Log.INFO -> LogcatLevel.INFO
                            Log.WARN -> LogcatLevel.WARNING
                            Log.ERROR, Log.ASSERT -> LogcatLevel.ERROR
                            else -> LogcatLevel.VERBOSE
                        }
                    val baseId = "${entry.time}_${entry.level}_${entry.tag}_${entry.message.hashCode()}"
                    val occurrence = occurrences.getOrDefault(baseId, 0)
                    occurrences[baseId] = occurrence + 1
                    LogcatRecord(
                        id = "${baseId}_$occurrence",
                        time = entry.time,
                        level = level,
                        tag = entry.tag,
                        message = entry.message,
                    )
                }
            }
    }

class FilterLogcatUseCase
    @Inject
    constructor() {
        operator fun invoke(
            entries: List<LogcatRecord>,
            query: String,
            levels: Set<LogcatLevel>,
        ): List<LogcatRecord> {
            val normalizedQuery = query.trim()
            return entries.filter { entry ->
                entry.level in levels &&
                    (
                        normalizedQuery.isEmpty() ||
                            entry.tag?.contains(normalizedQuery, ignoreCase = true) == true ||
                            entry.message.contains(normalizedQuery, ignoreCase = true)
                    )
            }
        }
    }

class ClearLogcatUseCase
    @Inject
    constructor(
        private val repository: LogcatRepository,
    ) {
        operator fun invoke() {
            repository.clear()
        }
    }

class FormatLogcatUseCase
    @Inject
    constructor() {
        operator fun invoke(entries: List<LogcatRecord>): String {
            val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return entries.joinToString(separator = "\n") { entry ->
                val timestamp = timestampFormat.format(Date(entry.time))
                val tag = entry.tag.orEmpty()
                "[$timestamp] ${entry.level.label}/$tag: ${entry.message}"
            }
        }
    }

class ExportLogcatUseCase
    @Inject
    constructor(
        private val formatLogcat: FormatLogcatUseCase,
        private val repository: LogcatRepository,
    ) {
        suspend operator fun invoke(entries: List<LogcatRecord>): Uri {
            val content =
                withContext(Dispatchers.Default) {
                    formatLogcat(entries)
                }
            return repository.exportText(content)
        }
    }
