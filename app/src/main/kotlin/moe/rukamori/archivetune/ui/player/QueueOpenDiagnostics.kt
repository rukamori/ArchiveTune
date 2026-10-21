/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.util.Log
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.LogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class QueueOpenAttempt(
    val id: Long,
    val startedAt: Long,
    val steps: List<String>,
)

internal object QueueOpenDiagnostics {
    private val lock = Any()
    private val _attempt = MutableStateFlow<QueueOpenAttempt?>(null)
    val attempt = _attempt.asStateFlow()
    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible = _dialogVisible.asStateFlow()
    private var nextId = 0L

    fun showDialog(): Unit {
        _dialogVisible.value = true
    }

    fun dismissDialog(): Unit {
        _dialogVisible.value = false
    }

    fun begin(context: String): Long =
        synchronized(lock) {
            val id = ++nextId
            val startedAt = System.currentTimeMillis()
            _attempt.value =
                QueueOpenAttempt(
                    id = id,
                    startedAt = startedAt,
                    steps = listOf("${formatTime(startedAt)} Queue button tapped ($context)"),
                )
            id
        }

    fun record(
        attemptId: Long,
        message: String,
    ) {
        synchronized(lock) {
            val current = _attempt.value?.takeIf { it.id == attemptId } ?: return
            _attempt.value =
                current.copy(
                    steps = current.steps + "${formatTime(System.currentTimeMillis())} $message",
                )
        }
    }

    fun recordCurrent(message: String) {
        val current = _attempt.value ?: return
        record(current.id, message)
    }

    private fun formatTime(time: Long): String =
        synchronized(timeFormat) {
            timeFormat.format(Date(time))
        }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
}

@Composable
internal fun QueueOpenDiagnosticsDialog(
    onDismiss: () -> Unit,
) {
    val attempt by QueueOpenDiagnostics.attempt.collectAsState()
    val appLogs by GlobalLog.logs.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val report = buildQueueOpenReport(attempt, appLogs)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.queue_open_diagnostics_title)) },
        text = {
            SelectionContainer {
                Text(
                    text = report,
                    modifier =
                        Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(end = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(report))
                    Toast.makeText(
                        context,
                        context.getString(R.string.copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Text(stringResource(R.string.copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}

private fun buildQueueOpenReport(
    attempt: QueueOpenAttempt?,
    appLogs: List<LogEntry>,
): String =
    buildString {
        appendLine("ArchiveTune queue-open diagnostics")
        if (attempt == null) {
            appendLine("No queue-open attempt has been recorded in this player session.")
            appendLine("Tap Queue once, then long-press Queue to capture an attempt.")
            return@buildString
        }

        appendLine("Attempt: ${attempt.id}")
        appendLine("Started: ${attempt.startedAt}")
        appendLine()
        appendLine("OPEN SEQUENCE")
        attempt.steps.forEachIndexed { index, step ->
            appendLine("${index + 1}. $step")
        }

        val errors =
            appLogs.filter { log ->
                log.time >= attempt.startedAt && log.level >= Log.WARN
            }
        appendLine()
        appendLine("APP WARNINGS / ERRORS SINCE ATTEMPT")
        if (errors.isEmpty()) {
            appendLine("No warning/error Timber logs were captured during this attempt.")
        } else {
            errors.forEach { entry ->
                appendLine(GlobalLog.format(entry))
                appendLine()
            }
        }
        appendLine("Note: warnings/errors are captured from the app's Timber log stream.")
    }
