/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.ui.theme.ArchiveTuneTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DebugActivity : ComponentActivity() {
    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        private const val CRASH_LOG_FILE_NAME = "Crashlog.txt"
        private const val SHARED_CRASH_LOG_DIRECTORY = "shared_crash_logs"
    }

    private var shareJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stack = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace available"
        val previewText = stack.lineSequence().firstOrNull()?.take(100) ?: "Unknown error"
        val timestampText =
            runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            }.getOrDefault("")
        val reportText = buildCrashReport(this, timestampText, stack)
        val deviceInfo = buildDeviceInfo(this)

        setContent {
            ArchiveTuneTheme {
                CrashReportScreen(
                    previewText = previewText,
                    timestampText = timestampText,
                    deviceInfo = deviceInfo,
                    stack = stack,
                    reportText = reportText,
                    onRestart = {
                        runCatching {
                            val intent =
                                Intent(this, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                }
                            startActivity(intent)
                        }
                    },
                    onShare = { shareCrashReport(reportText) },
                    onClose = { finish() },
                )
            }
        }
    }

    private fun shareCrashReport(reportText: String) {
        if (shareJob?.isActive == true) return

        shareJob =
            lifecycleScope.launch {
                try {
                    val crashLogUri =
                        withContext(Dispatchers.IO) {
                            val shareDirectory = File(cacheDir, SHARED_CRASH_LOG_DIRECTORY)
                            check(shareDirectory.exists() || shareDirectory.mkdirs())
                            val crashLogFile = File(shareDirectory, CRASH_LOG_FILE_NAME)
                            crashLogFile.writeText(reportText, Charsets.UTF_8)
                            FileProvider.getUriForFile(
                                this@DebugActivity,
                                "$packageName.FileProvider",
                                crashLogFile,
                            )
                        }

                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, crashLogUri)
                            clipData = ClipData.newRawUri(CRASH_LOG_FILE_NAME, crashLogUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_logs)))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Toast.makeText(this@DebugActivity, R.string.error_unknown, Toast.LENGTH_SHORT).show()
                }
            }
    }
}

@Composable
private fun CrashReportScreen(
    previewText: String,
    timestampText: String,
    deviceInfo: List<Pair<String, String>>,
    stack: String,
    reportText: String,
    onRestart: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        CrashReportScaffold(
            previewText = previewText,
            timestampText = timestampText,
            deviceInfo = deviceInfo,
            stack = stack,
            onCopyAll = { clipboard.setText(AnnotatedString(reportText)) },
            onShareAll = onShare,
            onRestart = onRestart,
            onClose = onClose,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashReportScaffold(
    previewText: String,
    timestampText: String,
    deviceInfo: List<Pair<String, String>>,
    stack: String,
    onCopyAll: () -> Unit,
    onShareAll: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onShareAll,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                    )
                },
                text = { Text("Share Logs") },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Crash Report",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                actions = {
                    IconButton(onClick = onCopyAll) {
                        Icon(
                            painter = painterResource(R.drawable.select_all),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.error),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = "Application crashed",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.92f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (timestampText.isNotBlank()) {
                        Text(
                            text = timestampText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f),
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Device info",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    deviceInfo.forEachIndexed { index, (k, v) ->
                        if (index != 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        }
                        KeyValueRow(
                            keyText = k,
                            valueText = v,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Stack trace",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    SelectionContainer {
                        Text(
                            text = stack,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("Restart")
                }
                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun KeyValueRow(
    keyText: String,
    valueText: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = keyText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f),
        )
    }
}

private fun buildCrashReport(
    context: android.content.Context,
    timestampText: String,
    stack: String,
): String {
    val deviceInfo = buildDeviceInfo(context)
    val packageName = context.packageName
    val versionName =
        runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            info.versionName ?: ""
        }.getOrDefault("")

    val versionCode =
        runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toString() else ""
        }.getOrDefault("")

    val header =
        buildString {
            appendLine("ArchiveTune crash report")
            if (timestampText.isNotBlank()) appendLine("Time: $timestampText")
            val appVersionLabel =
                when {
                    versionName.isNotBlank() && versionCode.isNotBlank() -> {
                        "${formatVersionName(versionName)} ($versionCode)"
                    }

                    versionName.isNotBlank() -> {
                        formatVersionName(versionName)
                    }

                    versionCode.isNotBlank() -> {
                        versionCode
                    }

                    else -> {
                        null
                    }
                }
            if (appVersionLabel != null) {
                appendLine("App: $appVersionLabel")
            }
            appendLine("Package: $packageName")
        }

    val deviceBlock =
        buildString {
            appendLine()
            appendLine("Device")
            deviceInfo.forEach { (k, v) ->
                appendLine("$k: $v")
            }
        }

    return buildString {
        append(header)
        append(deviceBlock)
        appendLine()
        appendLine()
        appendLine("Stack trace")
        appendLine(stack)
    }
}

private fun buildDeviceInfo(context: android.content.Context): List<Pair<String, String>> {
    val deviceName =
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { Settings.Secure.getString(context.contentResolver, "device_name") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            ?: Build.DEVICE

    val osVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"

    val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "-" }
    val brand = Build.BRAND.orEmpty().ifBlank { "-" }
    val model = Build.MODEL.orEmpty().ifBlank { "-" }

    val product = Build.PRODUCT.orEmpty().ifBlank { "-" }
    val hardware = Build.HARDWARE.orEmpty().ifBlank { "-" }
    val fingerprint = Build.FINGERPRINT.orEmpty().ifBlank { "-" }

    return listOf(
        "OS version" to osVersion,
        "Phone name" to deviceName,
        "Model" to model,
        "Manufacturer" to manufacturer,
        "Brand" to brand,
        "Device" to Build.DEVICE.orEmpty().ifBlank { "-" },
        "Product" to product,
        "Hardware" to hardware,
        "Fingerprint" to fingerprint,
    )
}
