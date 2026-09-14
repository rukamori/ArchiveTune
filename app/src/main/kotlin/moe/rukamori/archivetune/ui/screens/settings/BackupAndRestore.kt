/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.backup.ScheduledBackupFrequency
import moe.rukamori.archivetune.constants.ImportSourcePriorityKey
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.EnumListPreference
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialogOnline
import moe.rukamori.archivetune.ui.menu.LoadingScreen
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.BackupCategory
import moe.rukamori.archivetune.viewmodels.BackupRestoreViewModel
import moe.rukamori.archivetune.viewmodels.ExportPlaylistEvent
import moe.rukamori.archivetune.viewmodels.ExportPlaylistScreenState
import moe.rukamori.archivetune.viewmodels.ExportPlaylistUiModel
import moe.rukamori.archivetune.viewmodels.ScheduledBackupScreenState
import moe.rukamori.archivetune.viewmodels.ScheduledBackupUiData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CSV_MIME_TYPES =
    arrayOf(
        "text/csv",
        "text/x-csv",
        "text/comma-separated-values",
        "text/x-comma-separated-values",
        "application/csv",
        "application/x-csv",
        "application/vnd.ms-excel",
        "text/plain",
        "text/*",
        "application/octet-stream",
    )

@Composable
fun BackupAndRestore(
    navController: NavController,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val importedSongs = remember { mutableStateListOf<Song>() }
    var showChoosePlaylistDialogOnline by rememberSaveable { mutableStateOf(false) }
    var isProgressStarted by rememberSaveable { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var progressPercentage by rememberSaveable { mutableIntStateOf(0) }
    var showBackupOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreValidationError by rememberSaveable { mutableStateOf(false) }
    var restoreValidationErrorMessage by remember { mutableStateOf("") }
    var pendingBackupCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var pendingRestoreCategories by remember { mutableStateOf(BackupCategory.entries.toSet()) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val backupRestoreProgress by viewModel.backupRestoreProgress.collectAsStateWithLifecycle()
    val scheduledBackupState by viewModel.scheduledBackupState.collectAsStateWithLifecycle()
    val exportPlaylistState by viewModel.exportPlaylistState.collectAsStateWithLifecycle()
    val (importLocalFirst, onImportLocalFirstChange) = rememberPreference(ImportSourcePriorityKey, false)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.backupEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scheduledBackupEvent.collect { messageRes ->
            snackbarHostState.showSnackbar(context.getString(messageRes))
        }
    }

    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(context, uri, pendingBackupCategories)
            }
        }
    val backupDirectoryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let(viewModel::onScheduledBackupDirectorySelected)
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val result = viewModel.validateBackup(context, uri)
                    if (result.isValid) {
                        pendingRestoreCategories = result.availableCategories
                        pendingRestoreUri = uri
                        showRestoreOptionsDialog = true
                    } else {
                        restoreValidationErrorMessage = result.errorMessage ?: context.getString(R.string.restore_corrupted)
                        showRestoreValidationError = true
                    }
                }
            }
        }
    val importPlaylistFromCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val result = viewModel.importPlaylistFromCsv(context, uri)
                importedSongs.clear()
                importedSongs.addAll(result)
                if (importedSongs.isNotEmpty()) {
                    showChoosePlaylistDialogOnline = true
                }
            }
        }
    val importM3uLauncherOnline =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                val result = viewModel.loadM3UOnline(context, uri)
                importedSongs.clear()
                importedSongs.addAll(result)
                if (importedSongs.isNotEmpty()) {
                    showChoosePlaylistDialogOnline = true
                }
            }
        }
    val exportPlaylistLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            viewModel.onExportPlaylistDestinationSelected(uri)
        }

    LaunchedEffect(viewModel, context, exportPlaylistLauncher) {
        viewModel.exportPlaylistEvent.collect { event ->
            when (event) {
                is ExportPlaylistEvent.CreateDocument -> {
                    exportPlaylistLauncher.launch(event.suggestedFileName)
                }

                is ExportPlaylistEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore)) },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
                        .padding(16.dp),
            )
        },
    ) { innerPadding ->
        val topPadding = innerPadding.calculateTopPadding()

        Column(
            Modifier
                .padding(top = topPadding)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(bottom = SettingsDimensions.ScreenBottomPadding),
        ) {
            val scheduledBackupData =
                when (val state = scheduledBackupState) {
                    is ScheduledBackupScreenState.Success -> {
                        state.data
                    }

                    ScheduledBackupScreenState.Loading,
                    ScheduledBackupScreenState.Empty,
                    is ScheduledBackupScreenState.Error,
                    -> {
                        ScheduledBackupUiData(
                            enabled = false,
                            frequency = ScheduledBackupFrequency.WEEKLY,
                            customDateEpochDay = null,
                            customDateLabel = null,
                            directoryName = null,
                            overwriteExisting = false,
                            showCustomDatePicker = false,
                        )
                    }
                }

            ScheduledBackupSection(
                data = scheduledBackupData,
                enabled = scheduledBackupState !is ScheduledBackupScreenState.Loading,
                onEnabledChanged = viewModel::onScheduledBackupEnabledChanged,
                onFrequencySelected = viewModel::onScheduledBackupFrequencySelected,
                onDirectoryClick = { backupDirectoryLauncher.launch(null) },
                onOverwriteChanged = viewModel::onScheduledBackupOverwriteChanged,
            )

            GoogleDriveBackupSection(
                enabled = backupRestoreProgress == null && !showRestoreOptionsDialog && !showBackupOptionsDialog,
                onRestoreReady = remember(viewModel, context) {
                    { uri ->
                        coroutineScope.launch {
                            val result = viewModel.validateBackup(context, uri)
                            if (result.isValid) {
                                pendingRestoreCategories = result.availableCategories
                                pendingRestoreUri = uri
                                showRestoreOptionsDialog = true
                            } else {
                                restoreValidationErrorMessage = result.errorMessage ?: context.getString(R.string.restore_corrupted)
                                showRestoreValidationError = true
                            }
                        }
                    }
                },
            )

            PreferenceGroup(title = stringResource(R.string.internal_service)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.action_backup)) },
                        description = stringResource(R.string.backup_create_backup_desc),
                        icon = { Icon(painterResource(R.drawable.backup), null) },
                        onClick = { showBackupOptionsDialog = true },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.action_restore)) },
                        description = stringResource(R.string.restore_select_backup),
                        icon = { Icon(painterResource(R.drawable.restore), null) },
                        onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/zip")) },
                    )
                }

                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.import_priority_setting_title)) },
                        description = stringResource(R.string.import_priority_setting_desc),
                        icon = { Icon(painterResource(R.drawable.playlist_import), null) },
                        selectedValue = importLocalFirst,
                        values = listOf(true, false),
                        valueText = { localFirst ->
                            stringResource(
                                if (localFirst) {
                                    R.string.import_priority_local_first
                                } else {
                                    R.string.import_priority_youtube_only
                                },
                            )
                        },
                        valueDescription = { localFirst ->
                            stringResource(
                                if (localFirst) {
                                    R.string.import_priority_local_first_desc
                                } else {
                                    R.string.import_priority_youtube_only_desc
                                },
                            )
                        },
                        onValueSelected = onImportLocalFirstChange,
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.import_online)) },
                        description = stringResource(R.string.import_m3u_format),
                        icon = { Icon(painterResource(R.drawable.playlist_import), null) },
                        onClick = { importM3uLauncherOnline.launch(arrayOf("audio/*")) },
                    )
                }

                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.import_csv)) },
                        description = stringResource(R.string.import_csv_format),
                        icon = { Icon(painterResource(R.drawable.playlist_add), null) },
                        onClick = { importPlaylistFromCsv.launch(CSV_MIME_TYPES) },
                    )
                }

                item {
                    val exportState = exportPlaylistState
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.export_playlist_csv)) },
                        description =
                            stringResource(
                                when (exportState) {
                                    ExportPlaylistScreenState.Loading -> R.string.export_playlist_loading
                                    ExportPlaylistScreenState.Empty -> R.string.export_playlist_empty
                                    is ExportPlaylistScreenState.Error -> exportState.messageRes
                                    is ExportPlaylistScreenState.Success -> R.string.export_playlist_csv_description
                                },
                            ),
                        icon = { Icon(painterResource(R.drawable.download), null) },
                        onClick = viewModel::onExportPlaylistClick,
                        isEnabled =
                            exportState !is ExportPlaylistScreenState.Loading &&
                                (exportState as? ExportPlaylistScreenState.Success)?.isExporting != true,
                    )
                }
            }

        }
    }

    val scheduledBackupData = (scheduledBackupState as? ScheduledBackupScreenState.Success)?.data
    if (scheduledBackupData?.showCustomDatePicker == true) {
        ScheduledBackupDatePickerDialog(
            selectedEpochDay = scheduledBackupData.customDateEpochDay,
            onDateSelected = viewModel::onScheduledBackupCustomDateSelected,
            onDismiss = viewModel::onScheduledBackupCustomDateDismissed,
        )
    }

    if (showBackupOptionsDialog) {
        BackupOptionsDialog(
            title = stringResource(R.string.backup_options_title),
            confirmLabel = stringResource(R.string.action_backup),
            onConfirm = { categories ->
                pendingBackupCategories = categories
                showBackupOptionsDialog = false
                val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                backupLauncher.launch(
                    "${context.getString(R.string.app_name)}_${LocalDateTime.now().format(formatter)}.backup",
                )
            },
            onDismiss = { showBackupOptionsDialog = false },
        )
    }

    if (showRestoreOptionsDialog) {
        val uri = pendingRestoreUri
        if (uri != null) {
            BackupOptionsDialog(
                title = stringResource(R.string.restore_options_title),
                confirmLabel = stringResource(R.string.action_restore),
                onConfirm = { categories ->
                    pendingRestoreCategories = categories
                    showRestoreOptionsDialog = false
                    pendingRestoreUri = null
                    viewModel.restore(context, uri, categories)
                },
                onDismiss = {
                    showRestoreOptionsDialog = false
                    pendingRestoreUri = null
                },
            )
        }
    }

    if (showRestoreValidationError) {
        DefaultDialog(
            onDismiss = { showRestoreValidationError = false },
            title = { Text(stringResource(R.string.restore_failed)) },
            buttons = {
                TextButton(
                    onClick = { showRestoreValidationError = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Text(
                text = restoreValidationErrorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val exportState = exportPlaylistState
    if (exportState is ExportPlaylistScreenState.Success && exportState.isPickerVisible) {
        ExportPlaylistPickerDialog(
            playlists = exportState.playlists,
            onPlaylistSelected = viewModel::onExportPlaylistSelected,
            onDismiss = viewModel::onExportPlaylistPickerDismissed,
        )
    }

    AddToPlaylistDialogOnline(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        songs = importedSongs,
        onDismiss = { showChoosePlaylistDialogOnline = false },
        onProgressStart = { isProgressStarted = it },
        onPercentageChange = { progressPercentage = it },
        onStatusChange = { progressStatus = it },
    )

    LaunchedEffect(progressPercentage, isProgressStarted) {
        if (isProgressStarted && progressPercentage == 99) {
            delay(10_000)
            if (progressPercentage == 99) {
                isProgressStarted = false
                progressPercentage = 0
            }
        }
    }

    val isExportingPlaylist = (exportPlaylistState as? ExportPlaylistScreenState.Success)?.isExporting == true
    LoadingScreen(
        isVisible = backupRestoreProgress != null || isProgressStarted || isExportingPlaylist,
        value = backupRestoreProgress?.percent ?: progressPercentage,
        title =
            backupRestoreProgress?.title
                ?: if (isExportingPlaylist) stringResource(R.string.export_playlist_in_progress) else null,
        stepText =
            backupRestoreProgress?.step
                ?: if (isExportingPlaylist) stringResource(R.string.export_playlist_writing) else progressStatus,
        indeterminate = backupRestoreProgress?.indeterminate ?: isExportingPlaylist,
    )
}

@Composable
private fun ExportPlaylistPickerDialog(
    playlists: ImmutableList<ExportPlaylistUiModel>,
    onPlaylistSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogModifier =
        remember {
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
        }
    val listModifier =
        remember {
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
        }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = dialogModifier,
        title = { Text(stringResource(R.string.export_playlist_picker_title)) },
        constrainContentHeight = true,
        buttons = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        LazyColumn(
            modifier = listModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = playlists,
                key = ExportPlaylistUiModel::id,
                contentType = { ExportPlaylistUiModel::class },
            ) { playlist ->
                ExportPlaylistItem(
                    playlist = playlist,
                    onSelected = onPlaylistSelected,
                )
            }
        }
    }
}

@Composable
private fun ExportPlaylistItem(
    playlist: ExportPlaylistUiModel,
    onSelected: (String) -> Unit,
) {
    val onClick = remember(playlist.id, onSelected) { { onSelected(playlist.id) } }
    val itemModifier = remember { Modifier.fillMaxWidth() }
    val rowModifier =
        remember {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        }
    val playlistIconModifier = remember { Modifier.size(24.dp) }
    Surface(
        onClick = onClick,
        modifier = itemModifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val textModifier =
                remember {
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                }
            Icon(
                painter = painterResource(R.drawable.playlist_local),
                contentDescription = null,
                modifier = playlistIconModifier,
            )
            Column(modifier = textModifier) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                painter = painterResource(R.drawable.download),
                contentDescription = null,
                modifier = playlistIconModifier,
            )
        }
    }
}

@Composable
private fun ScheduledBackupSection(
    data: ScheduledBackupUiData,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onFrequencySelected: (ScheduledBackupFrequency) -> Unit,
    onDirectoryClick: () -> Unit,
    onOverwriteChanged: (Boolean) -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.scheduled_backup)) {
        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.scheduled_backup_enabled)) },
                description =
                    stringResource(
                        if (data.enabled) {
                            R.string.scheduled_backup_enabled_description
                        } else {
                            R.string.scheduled_backup_disabled_description
                        },
                    ),
                icon = { Icon(painterResource(R.drawable.repeat_on), contentDescription = null) },
                checked = data.enabled,
                onCheckedChange = onEnabledChanged,
                isEnabled = enabled,
            )
        }

        item {
            EnumListPreference(
                title = { Text(stringResource(R.string.scheduled_backup_frequency)) },
                description =
                    if (data.frequency == ScheduledBackupFrequency.CUSTOM && data.customDateLabel != null) {
                        stringResource(R.string.scheduled_backup_custom_date, data.customDateLabel)
                    } else {
                        stringResource(R.string.scheduled_backup_frequency_description)
                    },
                icon = { Icon(painterResource(R.drawable.calendar_today), contentDescription = null) },
                selectedValue = data.frequency,
                valueText = { frequency -> stringResource(frequency.labelRes) },
                onValueSelected = onFrequencySelected,
                isEnabled = enabled,
            )
        }

        item {
            PreferenceEntry(
                title = { Text(stringResource(R.string.scheduled_backup_directory)) },
                description =
                    data.directoryName
                        ?: stringResource(R.string.scheduled_backup_directory_description),
                icon = { Icon(painterResource(R.drawable.snippet_folder), contentDescription = null) },
                onClick = onDirectoryClick,
                isEnabled = enabled,
            )
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.scheduled_backup_overwrite)) },
                description = stringResource(R.string.scheduled_backup_overwrite_description),
                icon = { Icon(painterResource(R.drawable.backup), contentDescription = null) },
                checked = data.overwriteExisting,
                onCheckedChange = onOverwriteChanged,
                isEnabled = enabled && data.directoryName != null,
            )
        }
    }
}

@Composable
private fun ScheduledBackupDatePickerDialog(
    selectedEpochDay: Long?,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val initialEpochDay = selectedEpochDay?.coerceAtLeast(todayEpochDay) ?: todayEpochDay + 1
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis =
                LocalDate
                    .ofEpochDay(initialEpochDay)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli(),
            selectableDates =
                remember(todayEpochDay) {
                    object : androidx.compose.material3.SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                            Instant
                                .ofEpochMilli(utcTimeMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay() >= todayEpochDay
                    }
                },
        )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val epochDay =
                        Instant
                            .ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toEpochDay()
                    onDateSelected(epochDay)
                },
                enabled = datePickerState.selectedDateMillis != null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            title = { Text(stringResource(R.string.scheduled_backup_custom_title)) },
            showModeToggle = false,
        )
    }
}

private val ScheduledBackupFrequency.labelRes: Int
    get() =
        when (this) {
            ScheduledBackupFrequency.DAILY -> R.string.scheduled_backup_daily
            ScheduledBackupFrequency.WEEKLY -> R.string.scheduled_backup_weekly
            ScheduledBackupFrequency.MONTHLY -> R.string.scheduled_backup_monthly
            ScheduledBackupFrequency.CUSTOM -> R.string.scheduled_backup_custom
        }

@Composable
private fun IconBubble(
    icon: Painter,
    containerColor: Color,
    contentColor: Color,
    size: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(MaterialTheme.shapes.large)
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
private fun BackupOptionsDialog(
    title: String,
    confirmLabel: String,
    onConfirm: (Set<BackupCategory>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(BackupCategory.entries.toSet()) }

    DefaultDialog(
        onDismiss = onDismiss,
        title = { Text(title) },
        buttons = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = { onConfirm(selected) },
                shapes = ButtonDefaults.shapes(),
                enabled = selected.isNotEmpty(),
            ) {
                Text(confirmLabel)
            }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        BackupCategory.entries.forEach { category ->
            val isChecked = category in selected
            val labelRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.string.backup_category_library
                    BackupCategory.ACCOUNT -> R.string.backup_category_account
                    BackupCategory.SETTINGS -> R.string.backup_category_settings
                    BackupCategory.DOWNLOADS -> R.string.backup_category_downloads
                }
            val descRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.string.backup_category_library_desc
                    BackupCategory.ACCOUNT -> R.string.backup_category_account_desc
                    BackupCategory.SETTINGS -> R.string.backup_category_settings_desc
                    BackupCategory.DOWNLOADS -> R.string.backup_category_downloads_desc
                }
            val iconRes =
                when (category) {
                    BackupCategory.LIBRARY -> R.drawable.library_music
                    BackupCategory.ACCOUNT -> R.drawable.account
                    BackupCategory.SETTINGS -> R.drawable.settings
                    BackupCategory.DOWNLOADS -> R.drawable.download
                }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = Color.Transparent,
                onClick = {
                    selected = if (isChecked) selected - category else selected + category
                },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconBubble(
                        icon = painterResource(iconRes),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = 40.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + category else selected - category
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
