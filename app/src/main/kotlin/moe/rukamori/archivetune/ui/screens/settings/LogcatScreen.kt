/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.logcat.LogcatLevel
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.LogcatEffect
import moe.rukamori.archivetune.viewmodels.LogcatScreenState
import moe.rukamori.archivetune.viewmodels.LogcatUiEntries
import moe.rukamori.archivetune.viewmodels.LogcatUiEntry
import moe.rukamori.archivetune.viewmodels.LogcatUiModel
import moe.rukamori.archivetune.viewmodels.LogcatViewModel
import moe.rukamori.archivetune.ui.component.IconButton as ArchiveTuneIconButton

@Composable
fun LogcatScreen(
    navController: NavController,
    viewModel: LogcatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, context, snackbarHostState) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is LogcatEffect.Copy -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(null, effect.text))
                    snackbarHostState.showSnackbar(context.getString(effect.confirmationResId))
                }

                is LogcatEffect.Share -> {
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, effect.text)
                        }
                    context.startActivity(
                        Intent.createChooser(
                            shareIntent,
                            context.getString(R.string.share_logs),
                        ),
                    )
                }

                is LogcatEffect.Export -> {
                    val exportIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            clipData = ClipData.newRawUri("archivetune-log.txt", effect.uri)
                            putExtra(Intent.EXTRA_STREAM, effect.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    context.startActivity(
                        Intent.createChooser(
                            exportIntent,
                            context.getString(R.string.export),
                        ),
                    )
                }

                is LogcatEffect.Message -> {
                    snackbarHostState.showSnackbar(context.getString(effect.messageResId))
                }
            }
        }
    }

    LogcatScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onNavigateBack = navController::navigateUp,
        onNavigateBackLongClick = navController::backToMain,
        onQueryChange = viewModel::updateQuery,
        onToggleLevel = viewModel::toggleLevel,
        onTogglePaused = viewModel::togglePaused,
        onSetMenuExpanded = viewModel::setMenuExpanded,
        onClear = viewModel::clear,
        onShare = viewModel::share,
        onExport = viewModel::export,
        onCopy = viewModel::copy,
        onToggleExpanded = viewModel::toggleExpanded,
        onPauseAutoScroll = viewModel::pauseAutoScroll,
        onResumeAutoScroll = viewModel::resumeAutoScroll,
        onRetry = viewModel::retry,
    )
}

@Composable
private fun LogcatScreenContent(
    state: LogcatScreenState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateBackLongClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleLevel: (LogcatLevel) -> Unit,
    onTogglePaused: () -> Unit,
    onSetMenuExpanded: (Boolean) -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onCopy: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onPauseAutoScroll: () -> Unit,
    onResumeAutoScroll: () -> Unit,
    onRetry: () -> Unit,
) {
    val model =
        when (state) {
            is LogcatScreenState.Success -> state.model

            is LogcatScreenState.Empty -> state.model

            is LogcatScreenState.Error,
            LogcatScreenState.Loading,
            -> null
        }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val logUserScrollConnection =
        remember(onPauseAutoScroll) {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && (consumed.y != 0f || available.y != 0f)) {
                        onPauseAutoScroll()
                    }
                    return Offset.Zero
                }
            }
        }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LogcatTopBar(
                model = model,
                onNavigateBack = onNavigateBack,
                onNavigateBackLongClick = onNavigateBackLongClick,
                onTogglePaused = onTogglePaused,
                onSetMenuExpanded = onSetMenuExpanded,
                onClear = onClear,
                onShare = onShare,
                onExport = onExport,
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = model?.isAutoScrollPaused == true && model.entries.isNotEmpty(),
                modifier =
                    Modifier.windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
                    ),
                enter = scaleIn(),
                exit = scaleOut(),
            ) {
                FloatingActionButton(
                    onClick = onResumeAutoScroll,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_downward),
                        contentDescription = stringResource(R.string.jump_to_latest_log),
                    )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (state) {
                LogcatScreenState.Loading -> {
                    LoadingIndicator(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(48.dp),
                    )
                }

                is LogcatScreenState.Error -> {
                    LogcatErrorState(
                        message = stringResource(state.messageResId),
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is LogcatScreenState.Empty -> {
                    LogcatLogContent(
                        model = state.model,
                        entries = state.model.entries,
                        listState = listState,
                        logUserScrollConnection = logUserScrollConnection,
                        onQueryChange = onQueryChange,
                        onToggleLevel = onToggleLevel,
                        onCopy = onCopy,
                        onToggleExpanded = onToggleExpanded,
                    )
                }

                is LogcatScreenState.Success -> {
                    LogcatLogContent(
                        model = state.model,
                        entries = state.model.entries,
                        listState = listState,
                        logUserScrollConnection = logUserScrollConnection,
                        onQueryChange = onQueryChange,
                        onToggleLevel = onToggleLevel,
                        onCopy = onCopy,
                        onToggleExpanded = onToggleExpanded,
                    )
                }
            }
        }
    }

    LaunchedEffect(model?.entries?.size, model?.isAutoScrollPaused) {
        val entries = model?.entries ?: return@LaunchedEffect
        if (entries.isNotEmpty() && !model.isAutoScrollPaused) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }
}

@Composable
private fun LogcatTopBar(
    model: LogcatUiModel?,
    onNavigateBack: () -> Unit,
    onNavigateBackLongClick: () -> Unit,
    onTogglePaused: () -> Unit,
    onSetMenuExpanded: (Boolean) -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    MediumFlexibleTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.debug_logs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle = {
            Text(
                text = stringResource(R.string.filter_all_logs),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            ArchiveTuneIconButton(
                onClick = onNavigateBack,
                onLongClick = onNavigateBackLongClick,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            IconButton(
                onClick = onTogglePaused,
                enabled = model != null,
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (model?.isPaused == true) R.drawable.play else R.drawable.pause,
                        ),
                    contentDescription =
                        stringResource(
                            if (model?.isPaused == true) R.string.resume_logs else R.string.widget_pause,
                        ),
                )
            }
            Box {
                IconButton(
                    onClick = { onSetMenuExpanded(true) },
                    enabled = model != null,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.options_label),
                    )
                }
                DropdownMenu(
                    expanded = model?.isMenuExpanded == true,
                    onDismissRequest = { onSetMenuExpanded(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        onClick = onShare,
                        enabled = model?.entries?.isNotEmpty() == true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.share),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export)) },
                        onClick = onExport,
                        enabled =
                            model?.let { currentModel ->
                                currentModel.entries.isNotEmpty() && !currentModel.isExporting
                            } == true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.download),
                                contentDescription = null,
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.clear)) },
                        onClick = onClear,
                        enabled = model?.hasLogs == true,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.clear_all),
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun LogcatLogContent(
    model: LogcatUiModel,
    entries: LogcatUiEntries,
    listState: androidx.compose.foundation.lazy.LazyListState,
    logUserScrollConnection: NestedScrollConnection,
    onQueryChange: (String) -> Unit,
    onToggleLevel: (LogcatLevel) -> Unit,
    onCopy: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = model.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (model.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.clear),
                        )
                    }
                }
            },
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogcatLevel.entries.forEach { level ->
                FilterChip(
                    selected = level in model.selectedLevels,
                    onClick = { onToggleLevel(level) },
                    label = {
                        Text(stringResource(level.labelResId))
                    },
                    leadingIcon = {
                        LogcatLevelBadge(level = level)
                    },
                )
            }
        }

        if (entries.isEmpty()) {
            LogcatEmptyState(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .nestedScroll(logUserScrollConnection),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = entries,
                    key = { _, entry -> entry.id },
                    contentType = { _, entry -> entry.level },
                ) { index, entry ->
                    LogcatEntryItem(
                        entry = entry,
                        index = index,
                        count = entries.size,
                        onCopy = onCopy,
                        onToggleExpanded = onToggleExpanded,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogcatEntryItem(
    entry: LogcatUiEntry,
    index: Int,
    count: Int,
    onCopy: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
) {
    val levelColor = entry.level.color
    val onClick = remember(entry.id, onToggleExpanded) { { onToggleExpanded(entry.id) } }
    val onLongClick = remember(entry.id, onCopy) { { onCopy(entry.id) } }
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.copy_log_entry),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier = Modifier.fillMaxWidth(),
        overlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogcatLevelBadge(level = entry.level)
                Text(
                    text = entry.timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!entry.tag.isNullOrBlank()) {
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = levelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) {
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = levelColor,
            maxLines = if (entry.isExpanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LogcatLevelBadge(level: LogcatLevel) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = level.color,
        modifier = Modifier.size(24.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = level.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun LogcatEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.manage_search),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_logs),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.logs_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogcatErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

private val LogcatLevel.labelResId: Int
    get() =
        when (this) {
            LogcatLevel.VERBOSE -> R.string.log_level_verbose
            LogcatLevel.DEBUG -> R.string.log_level_debug
            LogcatLevel.INFO -> R.string.log_level_info
            LogcatLevel.WARNING -> R.string.log_level_warning
            LogcatLevel.ERROR -> R.string.log_level_error
        }

private val LogcatLevel.color: androidx.compose.ui.graphics.Color
    @Composable
    get() =
        when (this) {
            LogcatLevel.VERBOSE -> MaterialTheme.colorScheme.outline
            LogcatLevel.DEBUG -> MaterialTheme.colorScheme.secondary
            LogcatLevel.INFO -> MaterialTheme.colorScheme.primary
            LogcatLevel.WARNING -> MaterialTheme.colorScheme.tertiary
            LogcatLevel.ERROR -> MaterialTheme.colorScheme.error
        }
