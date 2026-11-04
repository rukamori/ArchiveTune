/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package moe.rukamori.archivetune

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.graphics.shapes.Morph
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
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
        private const val STATE_TIMESTAMP = "state_timestamp"
    }

    private var shareJob: Job? = null
    private var isSharing by mutableStateOf(false)
    private var timestampText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val stack = intent.getStringExtra(EXTRA_STACK_TRACE)?.takeIf { it.isNotBlank() }
        timestampText = savedInstanceState?.getString(STATE_TIMESTAMP)
            ?: runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            }.getOrDefault("")
        val appPackagePrefix = DebugActivity::class.java.name.substringBeforeLast('.')
        val versionLabel = resolveAppVersionLabel(this)
        val deviceInfo = buildDeviceInfo(this)
        val reportText =
            buildCrashReport(
                packageName = packageName,
                timestampText = timestampText,
                versionLabel = versionLabel,
                deviceInfo = deviceInfo,
                stack = stack ?: getString(R.string.crash_report_no_stack_trace),
            )
        val report =
            CrashReportUiModel(
                stack = stack,
                summary = stack?.let { parseCrashSummary(it, appPackagePrefix) },
                timestampText = timestampText,
                versionLabel = versionLabel,
                deviceInfo = deviceInfo,
                reportText = reportText,
                appPackagePrefix = appPackagePrefix,
            )

        setContent {
            ArchiveTuneTheme {
                CrashReportScreen(
                    report = report,
                    isSharing = isSharing,
                    onRestart = ::restartApp,
                    onShare = { shareCrashReport(reportText) },
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TIMESTAMP, timestampText)
    }

    private fun restartApp() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                },
            )
        } catch (_: RuntimeException) {
            Toast.makeText(this, R.string.crash_report_restart_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCrashReport(reportText: String) {
        if (shareJob?.isActive == true) return

        shareJob =
            lifecycleScope.launch {
                isSharing = true
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
                } finally {
                    isSharing = false
                }
            }
    }
}

@Immutable
private data class DeviceInfoEntry(
    @StringRes val labelRes: Int,
    val reportLabel: String,
    val value: String,
)

@Immutable
private data class CrashSummary(
    val exceptionType: String,
    val simpleName: String,
    val message: String?,
    val rootCause: String?,
    val origin: String?,
    val frameCount: Int,
)

@Immutable
private data class CrashReportUiModel(
    val stack: String?,
    val summary: CrashSummary?,
    val timestampText: String,
    val versionLabel: String?,
    val deviceInfo: List<DeviceInfoEntry>,
    val reportText: String,
    val appPackagePrefix: String,
)

@Immutable
private data class DiagnosisRow(
    @StringRes val labelRes: Int,
    val value: String,
    val monospace: Boolean,
)

private const val CollapsedStackLineCount = 14
private const val PrimaryDeviceInfoCount = 4
private const val EntranceStaggerMillis = 70L
private const val EmblemRotationMillis = 24_000
private val WideLayoutBreakpoint = 840.dp
private val CompactContentMaxWidth = 720.dp
private val ScreenHorizontalPadding = 20.dp
private val SectionSpacing = 28.dp
private val ToolbarClearance = 112.dp
private val EntranceOffset = 40.dp

@Composable
private fun CrashReportScreen(
    report: CrashReportUiModel,
    isSharing: Boolean,
    onRestart: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val layoutDirection = LocalLayoutDirection.current
    val toolbarScrollBehavior =
        FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)

    val copyToClipboard: (String, String) -> Unit = { label, text ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, text)))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(context.getString(R.string.copied))
            }
        }
    }
    val reportLabel = stringResource(R.string.crash_report_title)
    val onCopyReport = { copyToClipboard(reportLabel, report.reportText) }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(toolbarScrollBehavior),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = ToolbarClearance - FloatingToolbarDefaults.ScreenOffset),
            )
        },
    ) { innerPadding ->
        val startPadding = innerPadding.calculateStartPadding(layoutDirection) + ScreenHorizontalPadding
        val endPadding = innerPadding.calculateEndPadding(layoutDirection) + ScreenHorizontalPadding
        val topPadding = innerPadding.calculateTopPadding() + 24.dp
        val bottomPadding = innerPadding.calculateBottomPadding() + ToolbarClearance

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= WideLayoutBreakpoint) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(start = startPadding, end = endPadding),
                    horizontalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(top = topPadding, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                    ) {
                        CrashReportPrimarySections(report = report, onCopy = copyToClipboard)
                    }
                    Column(
                        modifier =
                            Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(top = topPadding, bottom = bottomPadding),
                    ) {
                        EntranceReveal(index = 1) {
                            StackTraceSection(
                                stack = report.stack,
                                frameCount = report.summary?.frameCount ?: 0,
                                appPackagePrefix = report.appPackagePrefix,
                                expandedByDefault = true,
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = startPadding, end = endPadding, top = topPadding, bottom = bottomPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .widthIn(max = CompactContentMaxWidth)
                                .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                    ) {
                        CrashReportPrimarySections(report = report, onCopy = copyToClipboard)
                        EntranceReveal(index = 3) {
                            StackTraceSection(
                                stack = report.stack,
                                frameCount = report.summary?.frameCount ?: 0,
                                appPackagePrefix = report.appPackagePrefix,
                                expandedByDefault = false,
                            )
                        }
                    }
                }
            }

            CrashReportToolbar(
                isSharing = isSharing,
                onCopyReport = onCopyReport,
                onShare = onShare,
                onClose = onClose,
                onRestart = onRestart,
                scrollBehavior = toolbarScrollBehavior,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .offset(y = -FloatingToolbarDefaults.ScreenOffset)
                        .zIndex(1f),
            )
        }
    }
}

@Composable
private fun CrashReportPrimarySections(
    report: CrashReportUiModel,
    onCopy: (String, String) -> Unit,
) {
    EntranceReveal(index = 0) {
        CrashHero(
            summary = report.summary,
            timestampText = report.timestampText,
            versionLabel = report.versionLabel,
        )
    }
    if (report.summary != null) {
        EntranceReveal(index = 1) {
            DiagnosisSection(summary = report.summary, onCopy = onCopy)
        }
    }
    EntranceReveal(index = 2) {
        DeviceInfoSection(entries = report.deviceInfo, onCopy = onCopy)
    }
}

@Composable
private fun EntranceReveal(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    var revealed by rememberSaveable { mutableStateOf(false) }
    val alpha = remember { Animatable(if (revealed) 1f else 0f) }
    val progress = remember { Animatable(if (revealed) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (revealed) return@LaunchedEffect
        delay(index * EntranceStaggerMillis)
        launch { alpha.animateTo(1f, motion.slowEffectsSpec()) }
        progress.animateTo(1f, motion.slowSpatialSpec())
        revealed = true
    }

    Box(
        modifier =
            modifier.graphicsLayer {
                this.alpha = alpha.value
                translationY = (1f - progress.value) * EntranceOffset.toPx()
            },
    ) {
        content()
    }
}

@Composable
private fun CrashHero(
    summary: CrashSummary?,
    timestampText: String,
    versionLabel: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CrashEmblem()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.crash_report_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = stringResource(R.string.crash_report_headline),
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.crash_report_supporting),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (summary != null) {
                CrashMetaPill(
                    iconRes = R.drawable.error,
                    text = summary.simpleName,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    monospace = true,
                )
            }
            if (timestampText.isNotBlank()) {
                CrashMetaPill(
                    iconRes = R.drawable.calendar_today,
                    text = timestampText,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (versionLabel != null) {
                CrashMetaPill(
                    iconRes = R.drawable.info,
                    text = versionLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CrashEmblem(modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    val emblemColor = MaterialTheme.colorScheme.errorContainer
    val haloColor = MaterialTheme.colorScheme.tertiaryContainer
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.SoftBurst) }
    val haloShape = remember { MaterialShapes.Cookie12Sided.toShape() }
    val morphProgress = remember { Animatable(0f) }
    val scale = remember { Animatable(0.4f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, motion.slowSpatialSpec()) }
        morphProgress.animateTo(1f, motion.slowSpatialSpec())
    }

    val rotation by
        rememberInfiniteTransition(label = "crashEmblem").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(EmblemRotationMillis, easing = LinearEasing)),
            label = "crashEmblemRotation",
        )

    Box(
        modifier =
            modifier
                .size(136.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        rotationZ = -rotation * 0.5f
                        alpha = 0.55f
                    }.background(haloColor, haloShape),
        )
        Box(
            modifier =
                Modifier
                    .size(104.dp)
                    .graphicsLayer { rotationZ = rotation }
                    .drawBehind {
                        drawOutline(
                            outline =
                                EditorialMorphShape(morph, morphProgress.value)
                                    .createOutline(size, layoutDirection, this),
                            color = emblemColor,
                        )
                    },
        )
        Icon(
            painter = painterResource(R.drawable.error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(44.dp),
        )
    }
}

@Composable
private fun CrashMetaPill(
    @DrawableRes iconRes: Int,
    text: String,
    containerColor: Color,
    contentColor: Color,
    monospace: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                style =
                    if (monospace) {
                        MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CrashSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun DiagnosisSection(
    summary: CrashSummary,
    onCopy: (String, String) -> Unit,
) {
    val rows =
        remember(summary) {
            buildList {
                add(DiagnosisRow(R.string.crash_report_exception, summary.exceptionType, monospace = true))
                summary.message?.let { add(DiagnosisRow(R.string.crash_report_message, it, monospace = false)) }
                summary.rootCause?.let { add(DiagnosisRow(R.string.crash_report_root_cause, it, monospace = true)) }
                summary.origin?.let { add(DiagnosisRow(R.string.crash_report_origin, it, monospace = true)) }
            }
        }
    val copyDescription = stringResource(R.string.crash_report_copy)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CrashSectionHeader(title = stringResource(R.string.crash_report_diagnosis))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                val label = stringResource(row.labelRes)
                SegmentedListItem(
                    onClick = { onCopy(label, row.value) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
                    overlineContent = { Text(label) },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.copy),
                            contentDescription = copyDescription,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                ) {
                    Text(
                        text = row.value,
                        style =
                            if (row.monospace) {
                                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoSection(
    entries: List<DeviceInfoEntry>,
    onCopy: (String, String) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val primaryEntries = entries.take(PrimaryDeviceInfoCount)
    val extraEntries = entries.drop(PrimaryDeviceInfoCount)
    val visibleCount = if (expanded) entries.size else primaryEntries.size
    val itemCount = visibleCount + if (extraEntries.isNotEmpty()) 1 else 0
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "deviceInfoChevron",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CrashSectionHeader(title = stringResource(R.string.crash_report_device))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            primaryEntries.forEachIndexed { index, entry ->
                DeviceInfoItem(entry = entry, index = index, count = itemCount, onCopy = onCopy)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    extraEntries.forEachIndexed { offset, entry ->
                        DeviceInfoItem(
                            entry = entry,
                            index = primaryEntries.size + offset,
                            count = itemCount,
                            onCopy = onCopy,
                        )
                    }
                }
            }
            if (extraEntries.isNotEmpty()) {
                SegmentedListItem(
                    onClick = { expanded = !expanded },
                    shapes = ListItemDefaults.segmentedShapes(index = itemCount - 1, count = itemCount),
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.expand_more),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                        )
                    },
                ) {
                    Text(
                        text =
                            stringResource(
                                if (expanded) R.string.crash_report_show_less_device else R.string.crash_report_show_all_device,
                            ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoItem(
    entry: DeviceInfoEntry,
    index: Int,
    count: Int,
    onCopy: (String, String) -> Unit,
) {
    val label = stringResource(entry.labelRes)
    SegmentedListItem(
        onClick = { onCopy(label, entry.value) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        overlineContent = { Text(label) },
    ) {
        Text(
            text = entry.value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StackTraceSection(
    stack: String?,
    frameCount: Int,
    appPackagePrefix: String,
    expandedByDefault: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val colorScheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    var wrapLines by rememberSaveable { mutableStateOf(true) }
    val containerColor = colorScheme.surfaceContainerHigh

    val highlightedTrace =
        remember(stack, appPackagePrefix, colorScheme.onSurface, colorScheme.error, colorScheme.primary, colorScheme.onSurfaceVariant) {
            stack?.let {
                buildStackTraceText(
                    stack = it,
                    appPackagePrefix = appPackagePrefix,
                    headlineColor = colorScheme.onSurface,
                    causeColor = colorScheme.error,
                    appFrameColor = colorScheme.primary,
                    frameColor = colorScheme.onSurfaceVariant,
                )
            }
        }
    val lineCount = remember(highlightedTrace) { highlightedTrace?.text?.count { it == '\n' }?.plus(1) ?: 0 }
    val collapsedLength =
        remember(highlightedTrace) {
            highlightedTrace?.text?.let { text ->
                var newlines = 0
                var end = text.length
                for (i in text.indices) {
                    if (text[i] == '\n' && ++newlines == CollapsedStackLineCount) {
                        end = i
                        break
                    }
                }
                end
            } ?: 0
        }
    val collapsible = highlightedTrace != null && collapsedLength < highlightedTrace.length
    val showCollapsed = collapsible && !expanded
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "stackTraceChevron",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CrashSectionHeader(
            title = stringResource(R.string.crash_report_stack_trace),
            supporting =
                if (frameCount > 0) {
                    pluralStringResource(R.plurals.crash_report_frame_count, frameCount, frameCount)
                } else {
                    null
                },
            trailing =
                if (highlightedTrace != null) {
                    {
                        ToggleButton(
                            checked = wrapLines,
                            onCheckedChange = { wrapLines = it },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.text_fields),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(R.string.crash_report_wrap_lines))
                        }
                    }
                } else {
                    null
                },
        )

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = motion.defaultSpatialSpec()),
                ) {
                    if (highlightedTrace == null) {
                        Text(
                            text = stringResource(R.string.crash_report_no_stack_trace),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    } else {
                        val visibleTrace =
                            if (showCollapsed) highlightedTrace.subSequence(0, collapsedLength) else highlightedTrace
                        SelectionContainer {
                            Text(
                                text = visibleTrace,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                softWrap = wrapLines,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .then(if (wrapLines) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                                        .padding(20.dp),
                            )
                        }
                        if (showCollapsed) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(72.dp)
                                        .background(
                                            Brush.verticalGradient(listOf(containerColor.copy(alpha = 0f), containerColor)),
                                        ),
                            )
                        }
                    }
                }
                if (collapsible) {
                    FilledTonalButton(
                        onClick = { expanded = !expanded },
                        shapes = ButtonDefaults.shapes(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.expand_more),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(ButtonDefaults.IconSize)
                                    .graphicsLayer { rotationZ = chevronRotation },
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text =
                                if (expanded) {
                                    stringResource(R.string.crash_report_collapse_trace)
                                } else {
                                    pluralStringResource(R.plurals.crash_report_show_full_trace, lineCount, lineCount)
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashReportToolbar(
    isSharing: Boolean,
    onCopyReport: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    onRestart: () -> Unit,
    scrollBehavior: FloatingToolbarScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        scrollBehavior = scrollBehavior,
    ) {
        IconButton(onClick = onCopyReport) {
            Icon(
                painter = painterResource(R.drawable.copy),
                contentDescription = stringResource(R.string.crash_report_copy_report),
            )
        }
        IconButton(
            onClick = onShare,
            enabled = !isSharing,
        ) {
            AnimatedContent(
                targetState = isSharing,
                transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "shareState",
            ) { sharing ->
                if (sharing) {
                    LoadingIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = stringResource(R.string.share_logs),
                    )
                }
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.crash_report_close),
            )
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onRestart,
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.replay),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.crash_report_restart))
        }
    }
}

private fun buildStackTraceText(
    stack: String,
    appPackagePrefix: String,
    headlineColor: Color,
    causeColor: Color,
    appFrameColor: Color,
    frameColor: Color,
): AnnotatedString {
    val lines = stack.trimEnd().lines()
    val appFramePrefix = "at $appPackagePrefix."
    return buildAnnotatedString {
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.replace("\t", "    ")
            val trimmed = line.trimStart()
            val style =
                when {
                    index == 0 -> SpanStyle(color = headlineColor, fontWeight = FontWeight.SemiBold)
                    trimmed.startsWith("Caused by:") -> SpanStyle(color = causeColor, fontWeight = FontWeight.SemiBold)
                    trimmed.startsWith(appFramePrefix) -> SpanStyle(color = appFrameColor)
                    else -> SpanStyle(color = frameColor)
                }
            withStyle(style) { append(line) }
            if (index != lines.lastIndex) append('\n')
        }
    }
}

private fun parseCrashSummary(
    stack: String,
    appPackagePrefix: String,
): CrashSummary {
    val lines =
        stack
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    val headline = lines.firstOrNull().orEmpty()
    val exceptionType = headline.substringBefore(':').trim()
    val message = headline.substringAfter(':', "").trim().ifEmpty { null }
    val rootCause =
        lines
            .lastOrNull { it.startsWith("Caused by:") }
            ?.removePrefix("Caused by:")
            ?.trim()
            ?.ifEmpty { null }
    val frames = lines.filter { it.startsWith("at ") }
    val origin =
        (frames.firstOrNull { it.startsWith("at $appPackagePrefix.") } ?: frames.firstOrNull())
            ?.removePrefix("at ")
    return CrashSummary(
        exceptionType = exceptionType,
        simpleName = exceptionType.substringAfterLast('.'),
        message = message,
        rootCause = rootCause,
        origin = origin,
        frameCount = frames.size,
    )
}

private fun resolveAppVersionLabel(context: Context): String? {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val versionName = info?.versionName.orEmpty()
    val versionCode =
        if (info != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toString() else ""
    return when {
        versionName.isNotBlank() && versionCode.isNotBlank() -> "${formatVersionName(versionName)} ($versionCode)"
        versionName.isNotBlank() -> formatVersionName(versionName)
        versionCode.isNotBlank() -> versionCode
        else -> null
    }
}

private fun buildCrashReport(
    packageName: String,
    timestampText: String,
    versionLabel: String?,
    deviceInfo: List<DeviceInfoEntry>,
    stack: String,
): String =
    buildString {
        appendLine("ArchiveTune crash report")
        if (timestampText.isNotBlank()) appendLine("Time: $timestampText")
        if (versionLabel != null) appendLine("App: $versionLabel")
        appendLine("Package: $packageName")
        appendLine()
        appendLine("Device")
        deviceInfo.forEach { entry ->
            appendLine("${entry.reportLabel}: ${entry.value}")
        }
        appendLine()
        appendLine()
        appendLine("Stack trace")
        appendLine(stack)
    }

private fun buildDeviceInfo(context: Context): List<DeviceInfoEntry> {
    val deviceName =
        runCatching { Settings.Global.getString(context.contentResolver, "device_name") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: runCatching { Settings.Secure.getString(context.contentResolver, "device_name") }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
            ?: Build.DEVICE

    return listOf(
        DeviceInfoEntry(
            R.string.crash_report_device_os,
            "OS version",
            "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
        ),
        DeviceInfoEntry(R.string.crash_report_device_name, "Phone name", deviceName),
        DeviceInfoEntry(R.string.crash_report_device_model, "Model", Build.MODEL.orEmpty().ifBlank { "-" }),
        DeviceInfoEntry(
            R.string.crash_report_device_manufacturer,
            "Manufacturer",
            Build.MANUFACTURER.orEmpty().ifBlank { "-" },
        ),
        DeviceInfoEntry(R.string.crash_report_device_brand, "Brand", Build.BRAND.orEmpty().ifBlank { "-" }),
        DeviceInfoEntry(R.string.crash_report_device_codename, "Device", Build.DEVICE.orEmpty().ifBlank { "-" }),
        DeviceInfoEntry(R.string.crash_report_device_product, "Product", Build.PRODUCT.orEmpty().ifBlank { "-" }),
        DeviceInfoEntry(R.string.crash_report_device_hardware, "Hardware", Build.HARDWARE.orEmpty().ifBlank { "-" }),
        DeviceInfoEntry(
            R.string.crash_report_device_fingerprint,
            "Fingerprint",
            Build.FINGERPRINT.orEmpty().ifBlank { "-" },
        ),
    )
}
