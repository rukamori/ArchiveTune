/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.os.Process
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.common.Player
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.GlobalLog
import moe.rukamori.archivetune.utils.LogEntry
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSettings(navController: NavController) {
    val (showDevDebug, onShowDevDebugChange) =
        rememberPreference(
            key = booleanPreferencesKey("dev_show_discord_debug"),
            defaultValue = false,
        )

    val (showNerdStats, onShowNerdStatsChange) =
        rememberPreference(
            key = booleanPreferencesKey("dev_show_nerd_stats"),
            defaultValue = false,
        )

    val (showCodecOnPlayer, onShowCodecOnPlayerChange) =
        rememberPreference(
            key = booleanPreferencesKey("show_codec_on_player"),
            defaultValue = false,
        )

    val playerConnection = LocalPlayerConnection.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.experiment_settings),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding: PaddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreferenceGroup(title = stringResource(R.string.experimental_features)) {
                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_discord_debug_ui)) },
                        description = stringResource(R.string.enable_discord_debug_lines),
                        icon = { Icon(painterResource(R.drawable.discord), null) },
                        checked = showDevDebug,
                        onCheckedChange = onShowDevDebugChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.show_nerd_stats)) },
                        description = stringResource(R.string.description_show_nerd_stats),
                        icon = { Icon(painterResource(R.drawable.stats), null) },
                        checked = showNerdStats,
                        onCheckedChange = onShowNerdStatsChange,
                    )
                }

                item {
                    SwitchPreference(
                        title = { Text(stringResource(R.string.display_codec_on_player)) },
                        description = stringResource(R.string.description_display_codec_on_player),
                        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                        checked = showCodecOnPlayer,
                        onCheckedChange = onShowCodecOnPlayerChange,
                    )
                }
            }

            AnimatedVisibility(
                visible = showDevDebug,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DiscordDebugSection()
                }
            }

            AnimatedVisibility(
                visible = showNerdStats && playerConnection != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NerdStatsSection(playerConnection = playerConnection)
                }
            }

            Spacer(modifier = Modifier.height(SettingsDimensions.ScreenBottomPadding))
        }
    }
}

@Composable
private fun DiscordDebugSection() {
    val lastStartTs: Long? by DiscordPresenceManager.lastRpcStartTimeFlow.collectAsState(initial = null)
    val lastEndTs: Long? by DiscordPresenceManager.lastRpcEndTimeFlow.collectAsState(initial = null)
    val lastStart: String = lastStartTs?.let { makeTimeString(it) } ?: "—"
    val lastEnd: String = lastEndTs?.let { makeTimeString(it) } ?: "—"
    val isRunning = DiscordPresenceManager.isRunning()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color =
                            if (isRunning) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.discord),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint =
                                    if (isRunning) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    },
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.discord_integration),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text =
                                if (isRunning) {
                                    stringResource(R.string.presence_manager_running)
                                } else {
                                    stringResource(R.string.presence_manager_stopped)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (isRunning) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (isRunning) {
                            Color(0xFF43B581).copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isRunning) Color(0xFF43B581) else MaterialTheme.colorScheme.error,
                                        shape = CircleShape,
                                    ),
                        )
                        Text(
                            text = if (isRunning) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRunning) Color(0xFF43B581) else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DebugTimestampItem(
                    label = stringResource(R.string.last_start),
                    value = lastStart,
                    icon = R.drawable.play,
                )
                DebugTimestampItem(
                    label = stringResource(R.string.last_end),
                    value = lastEnd,
                    icon = R.drawable.pause,
                )
            }
        }
    }

    LogViewerPanel()
}

@Composable
private fun DebugTimestampItem(
    label: String,
    value: String,
    icon: Int,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogViewerPanel() {
    val allLogs by GlobalLog.logs.collectAsState()
    var logcatEntries by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var clearTimestamp by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var filterMode by remember { mutableStateOf(0) }
    var selectedLevels by remember {
        mutableStateOf(setOf(Log.INFO, Log.WARN, Log.ERROR))
    }
    var levelsMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            logcatEntries = readLogcatEntries()
            delay(2000L)
        }
    }

    val allEntries =
        remember(allLogs, logcatEntries, clearTimestamp) {
            val seen = HashSet<String>()
            (allLogs + logcatEntries)
                .filter { it.time >= clearTimestamp }
                .sortedBy { it.time }
                .filter { entry ->
                    val key = "${entry.time / 100}|${entry.level}|${entry.message.take(80)}"
                    seen.add(key)
                }
        }

    val filtered =
        remember(allEntries, filterMode, selectedLevels) {
            allEntries.filter { entry ->
                val tagMatch =
                    when (filterMode) {
                        0 -> {
                            (entry.tag?.contains("DiscordRPC", true) == true) ||
                                (entry.tag?.contains("DiscordPresenceManager", true) == true) ||
                                entry.message.contains("DiscordPresenceManager") ||
                                entry.message.contains("DiscordRPC")
                        }

                        else -> {
                            true
                        }
                    }
                val levelMatch = selectedLevels.contains(entry.level)
                tagMatch && levelMatch
            }
        }

    val listState = rememberLazyListState()
    var isLogAutoScrollPaused by remember { mutableStateOf(false) }
    val isLogAtBottom by remember(filtered.size) {
        derivedStateOf {
            if (filtered.isEmpty()) {
                true
            } else {
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index == filtered.lastIndex
            }
        }
    }
    val logUserScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && (consumed.y != 0f || available.y != 0f)) {
                        isLogAutoScrollPaused = true
                    }
                    return Offset.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    isLogAutoScrollPaused = true
                    return Velocity.Zero
                }
            }
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.manage_search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.debug_logs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${filtered.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }

                Box {
                    FilledTonalIconButton(
                        onClick = { levelsMenuExpanded = true },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.filter_alt),
                            contentDescription = stringResource(R.string.filter_levels),
                        )
                    }

                    DropdownMenu(
                        expanded = levelsMenuExpanded,
                        onDismissRequest = { levelsMenuExpanded = false },
                    ) {
                        LogLevelMenuItem(
                            label = stringResource(R.string.log_level_verbose),
                            level = Log.VERBOSE,
                            selectedLevels = selectedLevels,
                            onToggle = { level ->
                                selectedLevels =
                                    if (selectedLevels.contains(level)) {
                                        selectedLevels - level
                                    } else {
                                        selectedLevels + level
                                    }
                            },
                        )
                        LogLevelMenuItem(
                            label = stringResource(R.string.log_level_debug),
                            level = Log.DEBUG,
                            selectedLevels = selectedLevels,
                            onToggle = { level ->
                                selectedLevels =
                                    if (selectedLevels.contains(level)) {
                                        selectedLevels - level
                                    } else {
                                        selectedLevels + level
                                    }
                            },
                        )
                        LogLevelMenuItem(
                            label = stringResource(R.string.log_level_info),
                            level = Log.INFO,
                            selectedLevels = selectedLevels,
                            onToggle = { level ->
                                selectedLevels =
                                    if (selectedLevels.contains(level)) {
                                        selectedLevels - level
                                    } else {
                                        selectedLevels + level
                                    }
                            },
                        )
                        LogLevelMenuItem(
                            label = stringResource(R.string.log_level_warning),
                            level = Log.WARN,
                            selectedLevels = selectedLevels,
                            onToggle = { level ->
                                selectedLevels =
                                    if (selectedLevels.contains(level)) {
                                        selectedLevels - level
                                    } else {
                                        selectedLevels + level
                                    }
                            },
                        )
                        LogLevelMenuItem(
                            label = stringResource(R.string.log_level_error),
                            level = Log.ERROR,
                            selectedLevels = selectedLevels,
                            onToggle = { level ->
                                selectedLevels =
                                    if (selectedLevels.contains(level)) {
                                        selectedLevels - level
                                    } else {
                                        selectedLevels + level
                                    }
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            onClick = {
                                selectedLevels = setOf(Log.INFO, Log.WARN, Log.ERROR)
                                levelsMenuExpanded = false
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.reset_to_default_levels),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(R.drawable.restore),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedButton(
                    selected = filterMode == 0,
                    onClick = { filterMode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { },
                ) {
                    Text(stringResource(R.string.filter_discord_only))
                }
                SegmentedButton(
                    selected = filterMode == 1,
                    onClick = { filterMode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { },
                ) {
                    Text(stringResource(R.string.filter_all_logs))
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp),
            ) {
                if (filtered.isEmpty()) {
                    EmptyLogPlaceholder()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .nestedScroll(logUserScrollConnection)
                                .nestedScroll(rememberNestedScrollInteropConnection())
                                .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(
                            items = filtered,
                            key = { _, entry -> "${entry.time}_${entry.level}_${entry.tag}_${entry.message.hashCode()}" },
                            contentType = { _, _ -> "log_entry" },
                        ) { _, entry ->
                            LogEntryItem(
                                entry = entry,
                                clipboard = clipboard,
                                coroutineScope = coroutineScope,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        GlobalLog.clear()
                        clearTimestamp = System.currentTimeMillis()
                    },
                    enabled = filtered.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.clear_all),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.clear))
                }

                FilledTonalButton(
                    onClick = {
                        if (filtered.isEmpty()) return@FilledTonalButton
                        val sb = StringBuilder()
                        filtered.forEach { sb.appendLine(GlobalLog.format(it)) }
                        val send =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, sb.toString())
                            }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_logs)))
                    },
                    enabled = filtered.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.share))
                }
            }
        }
    }

    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty() && !isLogAutoScrollPaused) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

    LaunchedEffect(isLogAtBottom, filtered.size) {
        if (isLogAtBottom) {
            isLogAutoScrollPaused = false
        }
    }
}

@Composable
private fun LogLevelMenuItem(
    label: String,
    level: Int,
    selectedLevels: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val cbStrokeWidthPx = with(LocalDensity.current) { floor(CheckboxDefaults.StrokeWidth.toPx()) }
    val cbCheckmarkStroke =
        remember(cbStrokeWidthPx) {
            Stroke(width = cbStrokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        }
    val cbOutlineStroke = remember(cbStrokeWidthPx) { Stroke(width = cbStrokeWidthPx) }
    DropdownMenuItem(
        onClick = { onToggle(level) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = selectedLevels.contains(level),
                    onCheckedChange = { onToggle(level) },
                    checkmarkStroke = cbCheckmarkStroke,
                    outlineStroke = cbOutlineStroke,
                )
                Text(label)
            }
        },
        leadingIcon = {
            LogLevelBadge(level = level, compact = true)
        },
    )
}

@Composable
private fun EmptyLogPlaceholder() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 400)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.anime_blank),
                contentDescription = null,
                modifier = Modifier.size(120.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.no_logs),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.logs_empty_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LogEntryItem(
    entry: LogEntry,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    val levelColor =
        when (entry.level) {
            Log.ERROR -> MaterialTheme.colorScheme.error
            Log.WARN -> MaterialTheme.colorScheme.tertiary
            Log.INFO -> MaterialTheme.colorScheme.primary
            Log.DEBUG -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.outline
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = levelColor.copy(alpha = 0.08f),
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { isExpanded = !isExpanded },
                    onLongClick = {
                        clipboard.setText(AnnotatedString(entry.message))
                        coroutineScope.launch {
                            GlobalLog.append(Log.INFO, "DebugSettings", copiedMessage)
                        }
                    },
                ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LogLevelBadge(level = entry.level)
                    Text(
                        text = DateFormat.format("HH:mm:ss", entry.time).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!entry.tag.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = entry.tag,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Text(
                text = if (isExpanded) entry.message else entry.message.lines().firstOrNull() ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = levelColor,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun LogLevelBadge(
    level: Int,
    compact: Boolean = false,
) {
    val (color, label) =
        when (level) {
            Log.VERBOSE -> MaterialTheme.colorScheme.outline to "V"
            Log.DEBUG -> MaterialTheme.colorScheme.secondary to "D"
            Log.INFO -> MaterialTheme.colorScheme.primary to "I"
            Log.WARN -> MaterialTheme.colorScheme.tertiary to "W"
            Log.ERROR -> MaterialTheme.colorScheme.error to "E"
            else -> MaterialTheme.colorScheme.outline to "?"
        }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color,
        modifier = Modifier.size(if (compact) 20.dp else 24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
                fontSize = if (compact) 10.sp else 12.sp,
            )
        }
    }
}

@Composable
private fun NerdStatsSection(playerConnection: moe.rukamori.archivetune.playback.PlayerConnection?) {
    if (playerConnection == null) return

    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val player = playerConnection.player

    var bufferPercentage by remember { mutableStateOf(0) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var currentPosition by remember { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            bufferPercentage = player.bufferedPercentage
            bufferedPosition = player.bufferedPosition
            currentPosition = player.currentPosition
            playbackSpeed = player.playbackParameters.speed
            delay(500)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.graphic_eq),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column {
                    Text(
                        text = stringResource(R.string.nerd_stats),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.real_time_playback_stats),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (mediaMetadata != null) {
                NerdStatCard(
                    icon = R.drawable.music_note,
                    label = stringResource(R.string.track_label),
                    value = mediaMetadata?.title ?: stringResource(R.string.no_track_playing),
                    accentColor = MaterialTheme.colorScheme.primary,
                )

                if (currentFormat != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NerdStatChip(
                            icon = R.drawable.graphic_eq,
                            label = stringResource(R.string.codec_label),
                            value =
                                currentFormat?.mimeType?.substringAfter("/")?.uppercase()
                                    ?: stringResource(R.string.unknown_codec),
                            modifier = Modifier.weight(1f),
                        )

                        val bitrateKbps = currentFormat?.bitrate?.let { it / 1000 } ?: 0
                        NerdStatChip(
                            icon = R.drawable.speed,
                            label = stringResource(R.string.bitrate_label),
                            value = if (bitrateKbps > 0) "$bitrateKbps kbps" else stringResource(R.string.unknown_bitrate),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val sampleRateKhz = currentFormat?.sampleRate?.let { (it / 1000.0).roundToInt() } ?: 0
                        NerdStatChip(
                            icon = R.drawable.waves,
                            label = stringResource(R.string.sample_rate_label),
                            value = if (sampleRateKhz > 0) "$sampleRateKhz kHz" else stringResource(R.string.unknown_sample_rate),
                            modifier = Modifier.weight(1f),
                        )

                        NerdStatChip(
                            icon = R.drawable.storage,
                            label = stringResource(R.string.content_length_label),
                            value =
                                currentFormat?.contentLength?.let {
                                    if (it > 0) {
                                        "${String.format("%.2f", it / 1024.0 / 1024.0)} MB"
                                    } else {
                                        stringResource(R.string.unknown_content_length)
                                    }
                                } ?: stringResource(R.string.unknown_content_length),
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LinearWavyProgressIndicator(
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = stringResource(R.string.loading_format),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val bufferDuration = ((bufferedPosition - currentPosition) / 1000.0).roundToInt()
                val bufferProgress by animateFloatAsState(
                    targetValue = bufferPercentage / 100f,
                    animationSpec = tween(300),
                    label = "buffer",
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.buffer_health_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "$bufferPercentage% ($bufferDuration sec ahead)",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    LinearWavyProgressIndicator(
                        progress = { bufferProgress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        color =
                            when {
                                bufferPercentage > 70 -> Color(0xFF43B581)
                                bufferPercentage > 30 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val playbackStateText =
                        when (player.playbackState) {
                            Player.STATE_IDLE -> stringResource(R.string.playback_state_idle)
                            Player.STATE_BUFFERING -> stringResource(R.string.playback_state_buffering)
                            Player.STATE_READY -> stringResource(R.string.playback_state_ready)
                            Player.STATE_ENDED -> stringResource(R.string.playback_state_ended)
                            else -> stringResource(R.string.playback_state_unknown)
                        }

                    val stateColor =
                        when (player.playbackState) {
                            Player.STATE_READY -> Color(0xFF43B581)
                            Player.STATE_BUFFERING -> MaterialTheme.colorScheme.tertiary
                            Player.STATE_IDLE -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.error
                        }

                    NerdStatChip(
                        icon = R.drawable.status,
                        label = stringResource(R.string.state_label),
                        value = playbackStateText,
                        modifier = Modifier.weight(1f),
                        valueColor = stateColor,
                    )

                    NerdStatChip(
                        icon = R.drawable.slow_motion_video,
                        label = stringResource(R.string.playback_speed_label),
                        value = "${playbackSpeed}x",
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.token),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.media_id_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = mediaMetadata?.id?.take(16)?.plus("...") ?: "N/A",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(48.dp)
                                    .alpha(0.5f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.no_track_playing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NerdStatCard(
    icon: Int,
    label: String,
    value: String,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.2f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accentColor,
                    )
                }
            }
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NerdStatChip(
    icon: Int,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private suspend fun readLogcatEntries(): List<LogEntry> =
    withContext(Dispatchers.IO) {
        val result = mutableListOf<LogEntry>()
        try {
            val pid = Process.myPid()
            val sdf = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val pattern = Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/(.+?)\(\s*\d+\):\s*(.*)$""")
            val process =
                ProcessBuilder("logcat", "--pid=$pid", "-v", "time", "-t", "500")
                    .redirectErrorStream(true)
                    .start()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { line ->
                    val match = pattern.matchEntire(line.trim()) ?: return@forEachLine
                    val (timeStr, levelChar, tag, message) = match.destructured
                    val level =
                        when (levelChar) {
                            "V" -> Log.VERBOSE
                            "D" -> Log.DEBUG
                            "I" -> Log.INFO
                            "W" -> Log.WARN
                            "E" -> Log.ERROR
                            else -> return@forEachLine
                        }
                    val cal = Calendar.getInstance()
                    cal.time = sdf.parse(timeStr) ?: return@forEachLine
                    cal.set(Calendar.YEAR, currentYear)
                    result.add(LogEntry(time = cal.timeInMillis, level = level, tag = tag.trim(), message = message))
                }
            }
            process.waitFor()
        } catch (_: Exception) {
        }
        result
    }
