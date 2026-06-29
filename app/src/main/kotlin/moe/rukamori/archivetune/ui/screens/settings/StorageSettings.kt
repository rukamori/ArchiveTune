/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.MaxCanvasCacheSizeKey
import moe.rukamori.archivetune.constants.MaxImageCacheSizeKey
import moe.rukamori.archivetune.constants.MaxSongCacheSizeKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.extensions.directorySizeBytes
import moe.rukamori.archivetune.extensions.tryOrNull
import moe.rukamori.archivetune.storage.StorageFolderKind
import moe.rukamori.archivetune.storage.StorageLocationKind
import moe.rukamori.archivetune.storage.StorageLocationRepository
import moe.rukamori.archivetune.ui.component.ActionPromptDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ListPreference
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SwitchPreference
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlaybackCache
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.StorageCacheClearUiKind
import moe.rukamori.archivetune.viewmodels.StorageCacheClearUiModel
import moe.rukamori.archivetune.viewmodels.StorageFolderUiModel
import moe.rukamori.archivetune.viewmodels.StorageLocationUiModel
import moe.rukamori.archivetune.viewmodels.StorageLocationUiOptions
import moe.rukamori.archivetune.viewmodels.StorageMigrationUiModel
import moe.rukamori.archivetune.viewmodels.StorageMigrationUiPhase
import moe.rukamori.archivetune.viewmodels.StorageSettingsScreenState
import moe.rukamori.archivetune.viewmodels.StorageSettingsViewModel

@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StorageSettings(
    navController: NavController,
    viewModel: StorageSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val imageDiskCache = context.imageLoader.diskCache ?: return
    val playerCache = LocalPlayerConnection.current?.service?.playerCache ?: return
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache ?: return
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val storagePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            snackbarHostState.currentSnackbarData?.dismiss()
            if (effect.restartApp) {
                snackbarHostState.showSnackbar(
                    message = context.getString(effect.messageResId),
                    duration = SnackbarDuration.Indefinite,
                )
            } else {
                snackbarHostState.showSnackbar(context.getString(effect.messageResId))
            }
        }
    }

    val downloadCacheDir =
        remember(context) {
            StorageLocationRepository.cacheDirectory(context, StorageFolderKind.DOWNLOADS)
        }
    val playerCacheDir =
        remember(context) {
            StorageLocationRepository.cacheDirectory(context, StorageFolderKind.SONG_CACHE)
        }
    val cacheSizeValues =
        remember {
            listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192)
        }
    val cacheSizeValuesWithUnlimited =
        remember {
            cacheSizeValues + (-1)
        }
    val canvasCacheSizeValues =
        remember {
            listOf(0, 64, 128, 256, 512, 1024, 2048, 4096, 8192, -1)
        }

    val (smartTrimmer, onSmartTrimmerChange) =
        rememberPreference(
            key = SmartTrimmerKey,
            defaultValue = false,
        )
    val (maxImageCacheSize, onMaxImageCacheSizeChange) =
        rememberPreference(
            key = MaxImageCacheSizeKey,
            defaultValue = 512,
        )
    val (maxSongCacheSize, onMaxSongCacheSizeChange) =
        rememberPreference(
            key = MaxSongCacheSizeKey,
            defaultValue = 1024,
        )
    val (maxCanvasCacheSize, onMaxCanvasCacheSizeChange) =
        rememberPreference(
            key = MaxCanvasCacheSizeKey,
            defaultValue = 256,
        )
    var clearCacheDialog by remember { mutableStateOf(false) }
    var clearDownloads by remember { mutableStateOf(false) }
    var clearImageCacheDialog by remember { mutableStateOf(false) }
    var clearCanvasCacheDialog by remember { mutableStateOf(false) }
    var imageCacheSize by remember { mutableStateOf(tryOrNull { imageDiskCache.size } ?: 0L) }
    var playerCacheSize by remember { mutableStateOf(0L) }
    var downloadCacheSize by remember { mutableStateOf(0L) }
    var canvasCacheBytes by remember { mutableStateOf(0L) }

    val maxImageCacheSizeBytes =
        if (maxImageCacheSize > 0) {
            cacheSizeMegabytesToBytes(maxImageCacheSize)
        } else {
            0L
        }
    val imageCacheProgress by animateFloatAsState(
        targetValue =
            if (maxImageCacheSizeBytes > 0) {
                (imageCacheSize.toFloat() / maxImageCacheSizeBytes).coerceIn(0f, 1f)
            } else {
                0f
            },
        label = "imageCacheProgress",
    )
    val maxSongCacheSizeBytes =
        if (maxSongCacheSize > 0) {
            cacheSizeMegabytesToBytes(maxSongCacheSize)
        } else {
            0L
        }
    val playerCacheProgress by animateFloatAsState(
        targetValue =
            if (maxSongCacheSizeBytes > 0) {
                (playerCacheSize.toFloat() / maxSongCacheSizeBytes).coerceIn(0f, 1f)
            } else {
                0f
            },
        label = "playerCacheProgress",
    )
    val canvasCacheProgress by animateFloatAsState(
        targetValue =
            if (maxCanvasCacheSize > 0) {
                val maxCanvasCacheSizeBytes = cacheSizeMegabytesToBytes(maxCanvasCacheSize)
                (canvasCacheBytes.toFloat() / maxCanvasCacheSizeBytes).coerceIn(0f, 1f)
            } else {
                0f
            },
        label = "canvasCacheProgress",
    )
    val isSmartTrimmerAvailable = maxImageCacheSize != 0 || maxSongCacheSize != 0

    LaunchedEffect(isSmartTrimmerAvailable) {
        if (!isSmartTrimmerAvailable && smartTrimmer) onSmartTrimmerChange(false)
    }
    LaunchedEffect(maxImageCacheSize) {
        if (maxImageCacheSize == 0) {
            viewModel.clearImageCache(showFeedback = false)
        }
    }
    LaunchedEffect(maxSongCacheSize) {
        if (maxSongCacheSize == 0) {
            viewModel.clearSongCache(showFeedback = false)
        }
    }
    LaunchedEffect(maxCanvasCacheSize) {
        CanvasArtworkPlaybackCache.setMaxSize(maxCanvasCacheSize)
        if (maxCanvasCacheSize == 0) {
            viewModel.clearCanvasCache(showFeedback = false)
        }
    }
    LaunchedEffect(imageDiskCache) {
        while (isActive) {
            delay(StorageRefreshIntervalMillis)
            imageCacheSize = tryOrNull { imageDiskCache.size } ?: 0L
        }
    }
    LaunchedEffect(playerCache, playerCacheDir) {
        while (isActive) {
            delay(StorageRefreshIntervalMillis)
            playerCacheSize =
                withContext(Dispatchers.IO) {
                    val cacheSpace = tryOrNull { playerCache.cacheSpace } ?: 0L
                    if (cacheSpace == 0L) playerCacheDir.directorySizeBytes() else cacheSpace
                }
        }
    }
    LaunchedEffect(downloadCache, downloadCacheDir) {
        while (isActive) {
            delay(StorageRefreshIntervalMillis)
            downloadCacheSize =
                withContext(Dispatchers.IO) {
                    val cacheSpace = tryOrNull { downloadCache.cacheSpace } ?: 0L
                    if (cacheSpace == 0L) downloadCacheDir.directorySizeBytes() else cacheSpace
                }
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            canvasCacheBytes =
                withContext(Dispatchers.IO) {
                    CanvasArtworkPlaybackCache.byteSize()
                }
            delay(StorageRefreshIntervalMillis)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.storage)) },
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
                .padding(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    bottom = SettingsDimensions.ScreenBottomPadding,
                ),
        ) {
            StorageFolderSection(
                state = screenState,
                smartTrimmer = smartTrimmer,
                isSmartTrimmerAvailable = isSmartTrimmerAvailable,
                onSmartTrimmerChange = onSmartTrimmerChange,
                onSelectFolder = viewModel::openStorageLocationPicker,
            )

            PreferenceGroup(title = stringResource(R.string.downloaded_songs)) {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_all_downloads)) },
                        description = stringResource(R.string.size_used, formatFileSize(downloadCacheSize)),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
                                contentDescription = null,
                            )
                        },
                        onClick = { clearDownloads = true },
                    )
                }
            }

            if (clearDownloads) {
                ActionPromptDialog(
                    title = stringResource(R.string.clear_all_downloads),
                    onDismiss = { clearDownloads = false },
                    onConfirm = {
                        viewModel.clearDownloads()
                        clearDownloads = false
                    },
                    onCancel = { clearDownloads = false },
                    content = {
                        Text(text = stringResource(R.string.clear_downloads_dialog))
                    },
                )
            }

            PreferenceGroup(title = stringResource(R.string.song_cache)) {
                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.max_song_cache_size)) },
                        description =
                            if (maxSongCacheSize == -1) {
                                stringResource(R.string.size_used, formatFileSize(playerCacheSize))
                            } else {
                                stringResource(
                                    R.string.storage_size_ratio,
                                    formatFileSize(playerCacheSize),
                                    formatFileSize(maxSongCacheSizeBytes),
                                )
                            },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_music),
                                contentDescription = null,
                            )
                        },
                        selectedValue = maxSongCacheSize,
                        values = cacheSizeValuesWithUnlimited,
                        valueText = {
                            when (it) {
                                0 -> stringResource(R.string.disable)
                                -1 -> stringResource(R.string.unlimited)
                                else -> formatFileSize(cacheSizeMegabytesToBytes(it))
                            }
                        },
                        onValueSelected = onMaxSongCacheSizeChange,
                    )
                }
                item(visible = maxSongCacheSize > 0) {
                    CacheUsagePreference(progress = playerCacheProgress)
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_song_cache)) },
                        onClick = { clearCacheDialog = true },
                    )
                }
            }

            if (clearCacheDialog) {
                ActionPromptDialog(
                    title = stringResource(R.string.clear_song_cache),
                    onDismiss = { clearCacheDialog = false },
                    onConfirm = {
                        viewModel.clearSongCache()
                        clearCacheDialog = false
                    },
                    onCancel = { clearCacheDialog = false },
                    content = {
                        Text(text = stringResource(R.string.clear_song_cache_dialog))
                    },
                )
            }

            PreferenceGroup(title = stringResource(R.string.image_cache)) {
                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.max_image_cache_size)) },
                        description =
                            when {
                                maxImageCacheSize < 0 -> {
                                    stringResource(R.string.size_used, formatFileSize(imageCacheSize))
                                }

                                maxImageCacheSize > 0 -> {
                                    stringResource(
                                        R.string.storage_size_ratio,
                                        formatFileSize(imageCacheSize),
                                        formatFileSize(maxImageCacheSizeBytes),
                                    )
                                }

                                else -> {
                                    stringResource(R.string.disable)
                                }
                            },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.image),
                                contentDescription = null,
                            )
                        },
                        selectedValue = maxImageCacheSize,
                        values = cacheSizeValuesWithUnlimited,
                        valueText = {
                            when (it) {
                                0 -> stringResource(R.string.disable)
                                -1 -> stringResource(R.string.unlimited)
                                else -> formatFileSize(cacheSizeMegabytesToBytes(it))
                            }
                        },
                        onValueSelected = onMaxImageCacheSizeChange,
                    )
                }
                item(visible = maxImageCacheSize > 0) {
                    CacheUsagePreference(progress = imageCacheProgress)
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_image_cache)) },
                        onClick = { clearImageCacheDialog = true },
                    )
                }
            }

            if (clearImageCacheDialog) {
                ActionPromptDialog(
                    title = stringResource(R.string.clear_image_cache),
                    onDismiss = { clearImageCacheDialog = false },
                    onConfirm = {
                        viewModel.clearImageCache()
                        clearImageCacheDialog = false
                    },
                    onCancel = { clearImageCacheDialog = false },
                    content = {
                        Text(text = stringResource(R.string.clear_image_cache_dialog))
                    },
                )
            }

            PreferenceGroup(title = stringResource(R.string.canvas_cache)) {
                item {
                    ListPreference(
                        title = { Text(stringResource(R.string.max_cache_size)) },
                        description =
                            when {
                                maxCanvasCacheSize < 0 -> {
                                    stringResource(R.string.size_used, formatFileSize(canvasCacheBytes))
                                }

                                maxCanvasCacheSize > 0 -> {
                                    stringResource(
                                        R.string.storage_size_ratio,
                                        formatFileSize(canvasCacheBytes),
                                        formatFileSize(cacheSizeMegabytesToBytes(maxCanvasCacheSize)),
                                    )
                                }

                                else -> {
                                    stringResource(R.string.disable)
                                }
                            },
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.motion_photos_on),
                                contentDescription = null,
                            )
                        },
                        selectedValue = maxCanvasCacheSize,
                        values = canvasCacheSizeValues,
                        valueText = {
                            when (it) {
                                0 -> stringResource(R.string.disable)
                                -1 -> stringResource(R.string.unlimited)
                                else -> formatFileSize(cacheSizeMegabytesToBytes(it))
                            }
                        },
                        onValueSelected = onMaxCanvasCacheSizeChange,
                    )
                }
                item(visible = maxCanvasCacheSize > 0) {
                    CacheUsagePreference(progress = canvasCacheProgress)
                }
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.clear_canvas_cache)) },
                        onClick = { clearCanvasCacheDialog = true },
                    )
                }
            }

            if (clearCanvasCacheDialog) {
                ActionPromptDialog(
                    title = stringResource(R.string.clear_canvas_cache),
                    onDismiss = { clearCanvasCacheDialog = false },
                    onConfirm = {
                        viewModel.clearCanvasCache()
                        clearCanvasCacheDialog = false
                    },
                    onCancel = { clearCanvasCacheDialog = false },
                    content = {
                        Text(text = stringResource(R.string.clear_canvas_cache_dialog))
                    },
                )
            }
        }
    }

    val successState = screenState as? StorageSettingsScreenState.Success
    if (successState?.model?.picker?.visible == true) {
        StorageLocationPickerSheet(
            options = successState.model.storageOptions,
            selectedOptionId = successState.model.picker.selectedOptionId ?: successState.model.folder.selectedOptionId,
            sheetState = storagePickerSheetState,
            onOptionSelected = viewModel::chooseStorageLocation,
            onDismiss = viewModel::dismissStorageLocationPicker,
            onConfirm = viewModel::applyStorageLocationSelection,
        )
    }
    successState?.model?.migration?.let { migration ->
        StorageMigrationProgressDialog(migration = migration)
    }
    successState?.model?.cacheClear?.let { cacheClear ->
        StorageCacheClearProgressDialog(cacheClear = cacheClear)
    }
}

@Composable
private fun StorageFolderSection(
    state: StorageSettingsScreenState,
    smartTrimmer: Boolean,
    isSmartTrimmerAvailable: Boolean,
    onSmartTrimmerChange: (Boolean) -> Unit,
    onSelectFolder: () -> Unit,
) {
    PreferenceGroup(title = stringResource(R.string.storage_folder)) {
        when (state) {
            StorageSettingsScreenState.Loading -> {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.storage_folder_pick)) },
                        description = stringResource(R.string.please_wait),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.snippet_folder),
                                contentDescription = null,
                            )
                        },
                        isEnabled = false,
                    )
                }
            }

            StorageSettingsScreenState.Empty -> {
                Unit
            }

            is StorageSettingsScreenState.Error -> {
                item {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.storage_folder_pick)) },
                        description = stringResource(state.messageResId),
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.error),
                                contentDescription = null,
                            )
                        },
                        onClick = onSelectFolder,
                    )
                }
            }

            is StorageSettingsScreenState.Success -> {
                item {
                    StorageFolderPreference(
                        folder = state.model.folder,
                        onSelectFolder = onSelectFolder,
                    )
                }
            }
        }

        item {
            SwitchPreference(
                title = { Text(stringResource(R.string.smart_trimmer)) },
                description = stringResource(R.string.smart_trimmer_description),
                checked = smartTrimmer && isSmartTrimmerAvailable,
                onCheckedChange = onSmartTrimmerChange,
                isEnabled = isSmartTrimmerAvailable,
            )
        }
    }
}

@Composable
private fun StorageFolderPreference(
    folder: StorageFolderUiModel,
    onSelectFolder: () -> Unit,
) {
    PreferenceEntry(
        title = { Text(storageLocationTitle(folder.kind, folder.volumeLabel)) },
        description = stringResource(R.string.storage_location_free, formatFileSize(folder.availableBytes)),
        icon = {
            Icon(
                painter = painterResource(R.drawable.snippet_folder),
                contentDescription = null,
            )
        },
        onClick = onSelectFolder,
    )
}

@Composable
private fun StorageMigrationProgressDialog(migration: StorageMigrationUiModel) {
    val progress = migration.percent / 100f
    val progressText =
        when (migration.phase) {
            StorageMigrationUiPhase.CACHE -> stringResource(R.string.storage_migration_cache_progress, migration.percent)
            StorageMigrationUiPhase.DOWNLOADS -> stringResource(R.string.storage_migration_downloads_progress, migration.percent)
        }

    BasicAlertDialog(
        onDismissRequest = {},
        modifier = Modifier.padding(24.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier =
                    Modifier
                        .size(280.dp)
                        .padding(24.dp),
            ) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.storage_migration_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StorageCacheClearProgressDialog(cacheClear: StorageCacheClearUiModel) {
    val progress = cacheClear.percent / 100f
    val progressText =
        when (cacheClear.kind) {
            StorageCacheClearUiKind.SONGS -> stringResource(R.string.storage_clear_song_cache_progress, cacheClear.percent)
            StorageCacheClearUiKind.DOWNLOADS -> stringResource(R.string.storage_clear_downloads_progress, cacheClear.percent)
            StorageCacheClearUiKind.IMAGES -> stringResource(R.string.storage_clear_image_cache_progress, cacheClear.percent)
            StorageCacheClearUiKind.CANVAS -> stringResource(R.string.storage_clear_canvas_cache_progress, cacheClear.percent)
        }

    BasicAlertDialog(
        onDismissRequest = {},
        modifier = Modifier.padding(24.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier =
                    Modifier
                        .size(280.dp)
                        .padding(24.dp),
            ) {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.storage_cache_clear_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StorageLocationPickerSheet(
    options: StorageLocationUiOptions,
    selectedOptionId: String,
    sheetState: SheetState,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val currentOptionId = options.firstOrNull { option -> option.isSelected }?.id
    val canApplySelection = currentOptionId != selectedOptionId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_location_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (index in 0 until options.size) {
                    val option = options[index]
                    StorageLocationOptionRow(
                        option = option,
                        selected = option.id == selectedOptionId,
                        onClick = { onOptionSelected(option.id) },
                    )
                }
            }

            Button(
                onClick = onConfirm,
                enabled = canApplySelection,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.storage_location_apply))
            }
        }
    }
}

@Composable
private fun StorageLocationOptionRow(
    option: StorageLocationUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.storage),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(18.dp))
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = storageLocationTitle(option.kind, option.volumeLabel),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.storage_location_free, formatFileSize(option.availableBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            if (selected) {
                Spacer(Modifier.width(16.dp))
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun storageLocationTitle(
    kind: StorageLocationKind,
    volumeLabel: String?,
): String =
    when (kind) {
        StorageLocationKind.INTERNAL -> {
            stringResource(R.string.storage_location_internal)
        }

        StorageLocationKind.REMOVABLE -> {
            volumeLabel
                ?.let { label -> stringResource(R.string.storage_location_removable_named, label) }
                ?: stringResource(R.string.storage_location_removable)
        }
    }

@Composable
private fun CacheUsagePreference(progress: Float) {
    val percentage = stringResource(R.string.percentage_format, (progress * 100).toInt())
    PreferenceEntry(
        title = {
            Text(stringResource(R.string.size_used, percentage))
        },
        content = {
            Spacer(Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        },
    )
}

private const val StorageRefreshIntervalMillis = 500L
private const val CacheSizeBytesPerMegabyte = 1024L * 1024L

private fun cacheSizeMegabytesToBytes(sizeMegabytes: Int): Long = sizeMegabytes.toLong().coerceAtLeast(0L) * CacheSizeBytesPerMegabyte
