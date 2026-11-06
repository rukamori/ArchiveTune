/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.MaxImageCacheSizeKey
import moe.rukamori.archivetune.constants.MaxSongCacheSizeKey
import moe.rukamori.archivetune.constants.SmartTrimmerKey
import moe.rukamori.archivetune.storage.StorageLocationKind
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
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
import moe.rukamori.archivetune.viewmodels.StorageUsageUiModel
import java.text.NumberFormat

private enum class StorageLimitTarget(
    @StringRes val titleRes: Int,
) {
    SONG_CACHE(R.string.max_song_cache_size),
    IMAGE_CACHE(R.string.max_image_cache_size),
}

private enum class StorageCategory(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @DrawableRes val iconRes: Int,
    @StringRes val clearTitleRes: Int,
    @StringRes val clearMessageRes: Int,
    val limitTarget: StorageLimitTarget?,
) {
    DOWNLOADS(
        titleRes = R.string.downloaded_songs,
        bodyRes = R.string.storage_category_downloads_body,
        iconRes = R.drawable.ic_download,
        clearTitleRes = R.string.clear_all_downloads,
        clearMessageRes = R.string.clear_downloads_dialog,
        limitTarget = null,
    ),
    SONG_CACHE(
        titleRes = R.string.song_cache,
        bodyRes = R.string.storage_category_song_cache_body,
        iconRes = R.drawable.ic_music,
        clearTitleRes = R.string.clear_song_cache,
        clearMessageRes = R.string.clear_song_cache_dialog,
        limitTarget = StorageLimitTarget.SONG_CACHE,
    ),
    IMAGE_CACHE(
        titleRes = R.string.image_cache,
        bodyRes = R.string.storage_category_image_cache_body,
        iconRes = R.drawable.image,
        clearTitleRes = R.string.clear_image_cache,
        clearMessageRes = R.string.clear_image_cache_dialog,
        limitTarget = StorageLimitTarget.IMAGE_CACHE,
    ),
    CANVAS_CACHE(
        titleRes = R.string.canvas_cache,
        bodyRes = R.string.storage_category_canvas_body,
        iconRes = R.drawable.slow_motion_video,
        clearTitleRes = R.string.clear_canvas_cache,
        clearMessageRes = R.string.clear_canvas_cache_dialog,
        limitTarget = null,
    ),
}

@Immutable
private data class StorageCategoryColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
fun StorageSettings(
    navController: NavController,
    viewModel: StorageSettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val screenState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val storagePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val limitSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
    var pendingClear by rememberSaveable { mutableStateOf<StorageCategory?>(null) }
    var limitTarget by rememberSaveable { mutableStateOf<StorageLimitTarget?>(null) }
    var entrancePlayed by rememberSaveable { mutableStateOf(false) }
    val entrance = remember { Animatable(if (entrancePlayed) 1f else 0f) }
    val isSmartTrimmerAvailable = maxImageCacheSize != 0 || maxSongCacheSize != 0

    LaunchedEffect(Unit) {
        if (!entrancePlayed) {
            entrance.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = StorageEntranceDurationMillis, easing = LinearEasing),
            )
            entrancePlayed = true
        }
    }
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

    val successModel = (screenState as? StorageSettingsScreenState.Success)?.model
    val usage = successModel?.usage
    val folder = successModel?.folder
    val playerInsets = LocalPlayerAwareWindowInsets.current
    val bottomInset = playerInsets.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val entranceProgress = { entrance.value }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.storage),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = {
                    Text(
                        text =
                            folder
                                ?.let { storageLocationTitle(it.kind, it.volumeLabel) }
                                ?: stringResource(R.string.please_wait),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .windowInsetsPadding(playerInsets.only(WindowInsetsSides.Bottom))
                        .padding(16.dp),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = bottomInset + SettingsDimensions.ScreenBottomPadding,
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .windowInsetsPadding(playerInsets.only(WindowInsetsSides.Horizontal)),
        ) {
            item(key = "overview", contentType = "overview") {
                StorageOverviewHero(
                    usage = usage,
                    folder = folder,
                    modifier = Modifier.storageEntrance(entranceProgress, index = 0),
                )
            }

            item(key = "location", contentType = "location") {
                val locationModifier =
                    Modifier
                        .animateItem()
                        .storageEntrance(entranceProgress, index = 1)
                when (val state = screenState) {
                    is StorageSettingsScreenState.Error -> {
                        StorageLocationErrorCard(
                            messageResId = state.messageResId,
                            modifier = locationModifier,
                        )
                    }

                    else -> {
                        StorageLocationCard(
                            folder = folder,
                            onChange = viewModel::openStorageLocationPicker,
                            modifier = locationModifier,
                        )
                    }
                }
            }

            item(key = "breakdown_header", contentType = "header") {
                StorageSectionHeader(
                    text = stringResource(R.string.storage_section_breakdown),
                    modifier = Modifier.storageEntrance(entranceProgress, index = 2),
                )
            }

            StorageCategory.entries.forEachIndexed { index, category ->
                item(key = category.name, contentType = "category") {
                    val limitMegabytes =
                        when (category.limitTarget) {
                            StorageLimitTarget.SONG_CACHE -> maxSongCacheSize
                            StorageLimitTarget.IMAGE_CACHE -> maxImageCacheSize
                            null -> null
                        }
                    StorageCategoryCard(
                        category = category,
                        sizeBytes = usage?.bytesFor(category),
                        limitMegabytes = limitMegabytes,
                        onLimitClick =
                            category.limitTarget?.let { target ->
                                { limitTarget = target }
                            },
                        onClear = { pendingClear = category },
                        modifier = Modifier.storageEntrance(entranceProgress, index = 3 + index),
                    )
                }
            }

            item(key = "cleanup_header", contentType = "header") {
                StorageSectionHeader(
                    text = stringResource(R.string.storage_section_cleanup),
                    modifier = Modifier.storageEntrance(entranceProgress, index = 7),
                )
            }

            item(key = "smart_trimmer", contentType = "trimmer") {
                SmartTrimmerCard(
                    checked = smartTrimmer && isSmartTrimmerAvailable,
                    available = isSmartTrimmerAvailable,
                    onCheckedChange = onSmartTrimmerChange,
                    modifier = Modifier.storageEntrance(entranceProgress, index = 8),
                )
            }
        }
    }

    pendingClear?.let { category ->
        StorageClearConfirmDialog(
            category = category,
            sizeBytes = usage?.bytesFor(category),
            onDismiss = { pendingClear = null },
            onConfirm = {
                pendingClear = null
                when (category) {
                    StorageCategory.DOWNLOADS -> viewModel.clearDownloads()
                    StorageCategory.SONG_CACHE -> viewModel.clearSongCache()
                    StorageCategory.IMAGE_CACHE -> viewModel.clearImageCache()
                    StorageCategory.CANVAS_CACHE -> viewModel.clearCanvasCache()
                }
            },
        )
    }

    limitTarget?.let { target ->
        StorageLimitSheet(
            target = target,
            selectedMegabytes =
                when (target) {
                    StorageLimitTarget.SONG_CACHE -> maxSongCacheSize
                    StorageLimitTarget.IMAGE_CACHE -> maxImageCacheSize
                },
            usedBytes =
                when (target) {
                    StorageLimitTarget.SONG_CACHE -> usage?.songCacheBytes
                    StorageLimitTarget.IMAGE_CACHE -> usage?.imageCacheBytes
                },
            sheetState = limitSheetState,
            onApply = { megabytes ->
                when (target) {
                    StorageLimitTarget.SONG_CACHE -> onMaxSongCacheSizeChange(megabytes)
                    StorageLimitTarget.IMAGE_CACHE -> onMaxImageCacheSizeChange(megabytes)
                }
                limitTarget = null
            },
            onDismiss = { limitTarget = null },
        )
    }

    if (successModel?.picker?.visible == true) {
        StorageLocationPickerSheet(
            options = successModel.storageOptions,
            selectedOptionId = successModel.picker.selectedOptionId ?: successModel.folder.selectedOptionId,
            sheetState = storagePickerSheetState,
            onOptionSelected = viewModel::chooseStorageLocation,
            onDismiss = viewModel::dismissStorageLocationPicker,
            onConfirm = viewModel::applyStorageLocationSelection,
        )
    }
    successModel?.migration?.let { migration ->
        StorageMigrationProgressDialog(migration = migration)
    }
    successModel?.cacheClear?.let { cacheClear ->
        StorageCacheClearProgressDialog(cacheClear = cacheClear)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageOverviewHero(
    usage: StorageUsageUiModel?,
    folder: StorageFolderUiModel?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val contentEnterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val contentExitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Surface(
        shape = RoundedCornerShape(StorageHeroCornerRadius),
        color = colorScheme.primaryContainer,
        contentColor = colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.storage_overview_label),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onPrimaryContainer.copy(alpha = StorageSecondaryTextAlpha),
            )

            AnimatedContent(
                targetState = usage == null,
                transitionSpec = {
                    fadeIn(contentEnterSpec) togetherWith fadeOut(contentExitSpec)
                },
                label = "storageOverviewContent",
            ) { measuring ->
                if (measuring || usage == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                    ) {
                        LoadingIndicator(
                            color = colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.storage_overview_measuring),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    StorageOverviewContent(
                        usage = usage,
                        folder = folder,
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageOverviewContent(
    usage: StorageUsageUiModel,
    folder: StorageFolderUiModel?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val totalBytes = usage.totalBytes
    val percentFormatter = rememberPercentFormatter()

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AnimatedFileSizeText(
                bytes = totalBytes,
                style = MaterialTheme.typography.displayMediumEmphasized,
                color = colorScheme.onPrimaryContainer,
            )
            val volumeTotal = folder?.totalBytes ?: 0L
            if (folder != null && volumeTotal > 0L) {
                Text(
                    text =
                        stringResource(
                            R.string.storage_overview_volume_share,
                            percentFormatter.format(totalBytes.toDouble() / volumeTotal),
                            storageLocationTitle(folder.kind, folder.volumeLabel),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onPrimaryContainer.copy(alpha = StorageSecondaryTextAlpha),
                )
            }
        }

        StorageBreakdownBar(
            usage = usage,
            trackColor = colorScheme.onPrimaryContainer.copy(alpha = StorageTrackAlpha),
        )

        if (totalBytes == 0L) {
            Text(
                text = stringResource(R.string.storage_overview_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onPrimaryContainer.copy(alpha = StorageSecondaryTextAlpha),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StorageCategory.entries.chunked(2).forEach { rowCategories ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowCategories.forEach { category ->
                            StorageLegendItem(
                                category = category,
                                bytes = usage.bytesFor(category),
                                totalBytes = totalBytes,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageBreakdownBar(
    usage: StorageUsageUiModel,
    trackColor: Color,
) {
    val totalBytes = usage.totalBytes
    Row(
        horizontalArrangement = Arrangement.spacedBy(StorageSegmentGap),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(StorageBreakdownBarHeight)
                .clip(CircleShape)
                .background(trackColor),
    ) {
        StorageCategory.entries.forEach { category ->
            val bytes = usage.bytesFor(category)
            val fraction by animateFloatAsState(
                targetValue = if (totalBytes > 0L) bytes.toFloat() / totalBytes else 0f,
                animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                label = "storageSegment${category.name}",
            )
            if (fraction > StorageSegmentMinFraction) {
                Box(
                    modifier =
                        Modifier
                            .weight(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(StorageSegmentCornerRadius))
                            .background(category.colors().accent),
                )
            }
        }
    }
}

@Composable
private fun StorageLegendItem(
    category: StorageCategory,
    bytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val percent = if (totalBytes > 0L) ((bytes.toDouble() / totalBytes) * 100).toInt() else 0
    val title = stringResource(category.titleRes)
    val sizeText = formatFileSize(bytes)
    val percentText = stringResource(R.string.percentage_format, percent)
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = "$title, $sizeText, $percentText"
            },
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 4.dp)
                    .size(12.dp)
                    .clip(MaterialShapes.Cookie4Sided.toShape())
                    .background(category.colors().accent),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.storage_legend_detail, sizeText, percentText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = StorageSecondaryTextAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StorageLocationCard(
    folder: StorageFolderUiModel?,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val percentFormatter = rememberPercentFormatter()
    Surface(
        shape = RoundedCornerShape(StorageCardCornerRadius),
        color = colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StorageShapedIcon(
                    shape = MaterialShapes.Cookie12Sided.toShape(),
                    iconRes = R.drawable.storage,
                    containerColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.storage_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                            folder
                                ?.let { storageLocationTitle(it.kind, it.volumeLabel) }
                                ?: stringResource(R.string.please_wait),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = onChange,
                    enabled = folder != null,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.storage_location_change))
                }
            }

            if (folder != null && folder.totalBytes > 0L) {
                val usedFraction =
                    ((folder.totalBytes - folder.availableBytes).toFloat() / folder.totalBytes).coerceIn(0f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = usedFraction,
                    animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                    label = "storageVolumeUsage",
                )
                LinearWavyProgressIndicator(
                    progress = { animatedFraction },
                    color = colorScheme.secondary,
                    trackColor = colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text =
                            stringResource(
                                R.string.storage_location_capacity,
                                formatFileSize(folder.availableBytes),
                                formatFileSize(folder.totalBytes),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.storage_location_used_percent, percentFormatter.format(usedFraction.toDouble())),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.secondary,
                    )
                }
            } else if (folder != null) {
                Text(
                    text = stringResource(R.string.storage_location_free, formatFileSize(folder.availableBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StorageLocationErrorCard(
    @StringRes messageResId: Int,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(StorageCardCornerRadius),
        color = colorScheme.errorContainer,
        contentColor = colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp),
        ) {
            StorageShapedIcon(
                shape = MaterialShapes.Cookie12Sided.toShape(),
                iconRes = R.drawable.error,
                containerColor = colorScheme.error,
                contentColor = colorScheme.onError,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.storage_folder),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(messageResId),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun StorageSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 12.dp, end = 8.dp)
                .semantics { heading() },
    )
}

@Composable
private fun StorageCategoryCard(
    category: StorageCategory,
    sizeBytes: Long?,
    limitMegabytes: Int?,
    onLimitClick: (() -> Unit)?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val colors = category.colors()
    val limitBytes = limitMegabytes?.takeIf { it > 0 }?.let(::cacheSizeMegabytesToBytes)
    val isCacheOff = limitMegabytes == 0

    Surface(
        shape = RoundedCornerShape(StorageCardCornerRadius),
        color = colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StorageShapedIcon(
                    shape = category.iconShape(),
                    iconRes = category.iconRes,
                    containerColor = colors.container,
                    contentColor = colors.onContainer,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(category.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(category.bodyRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                if (sizeBytes == null) {
                    Text(
                        text = stringResource(R.string.please_wait),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colorScheme.onSurfaceVariant,
                    )
                } else {
                    AnimatedFileSizeText(
                        bytes = sizeBytes,
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                        color = colors.accent,
                    )
                }
                if (limitBytes != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "/ ${formatFileSize(limitBytes)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = limitBytes != null && sizeBytes != null,
                enter =
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit =
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                val target =
                    if (limitBytes != null && sizeBytes != null) {
                        (sizeBytes.toFloat() / limitBytes).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                val progress by animateFloatAsState(
                    targetValue = target,
                    animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
                    label = "storageCategoryLimit${category.name}",
                )
                LinearWavyProgressIndicator(
                    progress = { progress },
                    color = colors.accent,
                    trackColor = colors.container,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (isCacheOff) {
                Text(
                    text = stringResource(R.string.storage_cache_off),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onLimitClick != null && limitMegabytes != null) {
                    OutlinedButton(
                        onClick = onLimitClick,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.tune),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(
                            text = stringResource(R.string.storage_limit_value, cacheLimitLabel(limitMegabytes)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(
                    onClick = onClear,
                    enabled = sizeBytes != 0L,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.clear))
                }
            }
        }
    }
}

@Composable
private fun SmartTrimmerCard(
    checked: Boolean,
    available: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (checked) colorScheme.tertiaryContainer else colorScheme.surfaceContainerLow,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "smartTrimmerContainer",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Sunny) }
    val morphProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "smartTrimmerMorph",
    )

    Surface(
        shape = RoundedCornerShape(StorageCardCornerRadius),
        color = containerColor,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StorageCardCornerRadius))
                .toggleable(
                    value = checked,
                    enabled = available,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp),
        ) {
            StorageShapedIcon(
                shape = EditorialMorphShape(morph, morphProgress),
                iconRes = R.drawable.auto_awesome,
                containerColor = if (checked) colorScheme.tertiary else colorScheme.surfaceContainerHighest,
                contentColor = if (checked) colorScheme.onTertiary else colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.smart_trimmer),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (available) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = StorageDisabledAlpha),
                )
                Text(
                    text =
                        stringResource(
                            if (available) R.string.smart_trimmer_description else R.string.storage_smart_trimmer_unavailable,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = available,
                thumbContent =
                    if (checked) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}

@Composable
private fun StorageClearConfirmDialog(
    category: StorageCategory,
    sizeBytes: Long?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.delete),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(category.clearTitleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(category.clearMessageRes).trim())
                if (sizeBytes != null && sizeBytes > 0L) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.storage_clear_frees, formatFileSize(sizeBytes)),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageLimitSheet(
    target: StorageLimitTarget,
    selectedMegabytes: Int,
    usedBytes: Long?,
    sheetState: SheetState,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    var pendingMegabytes by rememberSaveable(target) { mutableIntStateOf(selectedMegabytes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(target.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.storage_limit_sheet_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )
                if (usedBytes != null) {
                    Text(
                        text = stringResource(R.string.size_used, formatFileSize(usedBytes)),
                        style = MaterialTheme.typography.labelLarge,
                        color = colorScheme.primary,
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
            ) {
                CacheLimitValuesMegabytes.forEach { megabytes ->
                    val checked = megabytes == pendingMegabytes
                    ToggleButton(
                        checked = checked,
                        onCheckedChange = { pendingMegabytes = megabytes },
                    ) {
                        AnimatedVisibility(visible = checked) {
                            Row {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            }
                        }
                        Text(cacheLimitLabel(megabytes))
                    }
                }
            }

            AnimatedVisibility(
                visible = pendingMegabytes == 0 && selectedMegabytes != 0,
                enter =
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit =
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                StorageWarningBanner(text = stringResource(R.string.storage_limit_disable_warning))
            }

            Button(
                onClick = { onApply(pendingMegabytes) },
                enabled = pendingMegabytes != selectedMegabytes,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
            ) {
                Text(stringResource(R.string.storage_limit_apply))
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
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.storage_location_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.storage_location_sheet_supporting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
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

            AnimatedVisibility(
                visible = canApplySelection,
                enter =
                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit =
                    fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()),
            ) {
                StorageWarningBanner(text = stringResource(R.string.storage_migration_warning))
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
    val colorScheme = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "storageOptionContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "storageOptionContent",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) StorageCardCornerRadius else StorageOptionCornerRadius,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "storageOptionCorner",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val morphProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "storageOptionMorph",
    )
    val rowShape = RoundedCornerShape(cornerRadius)

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = rowShape,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            StorageShapedIcon(
                shape = EditorialMorphShape(morph, morphProgress),
                iconRes = R.drawable.storage,
                containerColor = if (selected) colorScheme.primary else colorScheme.surfaceContainerHighest,
                contentColor = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = storageLocationTitle(option.kind, option.volumeLabel),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (option.isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = colorScheme.tertiaryContainer,
                            contentColor = colorScheme.onTertiaryContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.storage_location_current),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text =
                        if (option.totalBytes > 0L) {
                            stringResource(
                                R.string.storage_location_capacity,
                                formatFileSize(option.availableBytes),
                                formatFileSize(option.totalBytes),
                            )
                        } else {
                            stringResource(R.string.storage_location_free, formatFileSize(option.availableBytes))
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                )
                if (option.totalBytes > 0L) {
                    val usedFraction =
                        ((option.totalBytes - option.availableBytes).toFloat() / option.totalBytes).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { usedFraction },
                        color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                        trackColor = if (selected) colorScheme.surfaceContainerLowest else colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            RadioButton(
                selected = selected,
                onClick = null,
            )
        }
    }
}

@Composable
private fun StorageMigrationProgressDialog(migration: StorageMigrationUiModel) {
    StorageOperationDialog(
        percent = migration.percent,
        title =
            when (migration.phase) {
                StorageMigrationUiPhase.CACHE -> stringResource(R.string.storage_migration_cache_title)
                StorageMigrationUiPhase.DOWNLOADS -> stringResource(R.string.storage_migration_downloads_title)
            },
        warning = stringResource(R.string.storage_migration_warning),
    )
}

@Composable
private fun StorageCacheClearProgressDialog(cacheClear: StorageCacheClearUiModel) {
    StorageOperationDialog(
        percent = cacheClear.percent,
        title =
            when (cacheClear.kind) {
                StorageCacheClearUiKind.SONGS -> stringResource(R.string.storage_clear_song_cache_title)
                StorageCacheClearUiKind.DOWNLOADS -> stringResource(R.string.storage_clear_downloads_title)
                StorageCacheClearUiKind.IMAGES -> stringResource(R.string.storage_clear_image_cache_title)
                StorageCacheClearUiKind.CANVAS -> stringResource(R.string.storage_clear_canvas_cache_title)
            },
        warning = stringResource(R.string.storage_cache_clear_warning),
    )
}

@Composable
private fun StorageOperationDialog(
    percent: Int,
    title: String,
    warning: String,
) {
    val progress by animateFloatAsState(
        targetValue = percent.coerceIn(0, 100) / 100f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "storageOperationProgress",
    )

    BasicAlertDialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(StorageHeroCornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(StorageOperationIndicatorSize),
                    )
                    Text(
                        text = stringResource(R.string.percentage_format, percent.coerceIn(0, 100)),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                StorageWarningBanner(text = warning)
            }
        }
    }
}

@Composable
private fun StorageWarningBanner(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.info),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StorageShapedIcon(
    shape: Shape,
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    size: Dp = StorageShapedIconSize,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(size)
                .clip(shape)
                .background(containerColor),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun AnimatedFileSizeText(
    bytes: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    var hasCounted by rememberSaveable { mutableStateOf(false) }
    val animatedBytes = remember { Animatable(if (hasCounted) bytes.toFloat() else 0f) }
    LaunchedEffect(bytes) {
        animatedBytes.animateTo(
            targetValue = bytes.toFloat(),
            animationSpec = tween(durationMillis = StorageCountDurationMillis, easing = FastOutSlowInEasing),
        )
        hasCounted = true
    }
    val finalText = formatFileSize(bytes)
    Text(
        text = if (animatedBytes.isRunning) formatFileSize(animatedBytes.value.toLong()) else finalText,
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics { contentDescription = finalText },
    )
}

@Composable
private fun rememberPercentFormatter(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        NumberFormat.getPercentInstance(locale).apply {
            maximumFractionDigits = 1
        }
    }
}

@Composable
private fun cacheLimitLabel(megabytes: Int): String =
    when (megabytes) {
        0 -> stringResource(R.string.disable)
        -1 -> stringResource(R.string.unlimited)
        else -> formatFileSize(cacheSizeMegabytesToBytes(megabytes))
    }

@Composable
private fun StorageCategory.colors(): StorageCategoryColors {
    val colorScheme = MaterialTheme.colorScheme
    return when (this) {
        StorageCategory.DOWNLOADS -> {
            StorageCategoryColors(colorScheme.primary, colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
        }

        StorageCategory.SONG_CACHE -> {
            StorageCategoryColors(colorScheme.tertiary, colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        }

        StorageCategory.IMAGE_CACHE -> {
            StorageCategoryColors(colorScheme.secondary, colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
        }

        StorageCategory.CANVAS_CACHE -> {
            StorageCategoryColors(colorScheme.onSurfaceVariant, colorScheme.surfaceContainerHighest, colorScheme.onSurface)
        }
    }
}

@Composable
private fun StorageCategory.iconShape(): Shape =
    when (this) {
        StorageCategory.DOWNLOADS -> MaterialShapes.Cookie9Sided.toShape()
        StorageCategory.SONG_CACHE -> MaterialShapes.SoftBurst.toShape()
        StorageCategory.IMAGE_CACHE -> MaterialShapes.Clover4Leaf.toShape()
        StorageCategory.CANVAS_CACHE -> MaterialShapes.Arch.toShape()
    }

private fun StorageUsageUiModel.bytesFor(category: StorageCategory): Long =
    when (category) {
        StorageCategory.DOWNLOADS -> downloadsBytes
        StorageCategory.SONG_CACHE -> songCacheBytes
        StorageCategory.IMAGE_CACHE -> imageCacheBytes
        StorageCategory.CANVAS_CACHE -> canvasCacheBytes
    }

private fun Modifier.storageEntrance(
    progress: () -> Float,
    index: Int,
): Modifier =
    graphicsLayer {
        val start = index * StorageEntranceStagger
        val local = ((progress() - start) / StorageEntranceWindow).coerceIn(0f, 1f)
        val eased = FastOutSlowInEasing.transform(local)
        alpha = eased
        translationY = (1f - eased) * StorageEntranceOffset.toPx()
        val scale = StorageEntranceStartScale + (1f - StorageEntranceStartScale) * eased
        scaleX = scale
        scaleY = scale
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

private val CacheLimitValuesMegabytes = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1)
private val StorageHeroCornerRadius = 32.dp
private val StorageCardCornerRadius = 28.dp
private val StorageOptionCornerRadius = 16.dp
private val StorageShapedIconSize = 48.dp
private val StorageOperationIndicatorSize = 112.dp
private val StorageBreakdownBarHeight = 20.dp
private val StorageSegmentGap = 3.dp
private val StorageSegmentCornerRadius = 6.dp
private val StorageEntranceOffset = 28.dp
private const val StorageSegmentMinFraction = 0.002f
private const val StorageSecondaryTextAlpha = 0.78f
private const val StorageTrackAlpha = 0.12f
private const val StorageDisabledAlpha = 0.38f
private const val StorageEntranceDurationMillis = 900
private const val StorageEntranceStagger = 0.06f
private const val StorageEntranceWindow = 1f - 8 * StorageEntranceStagger
private const val StorageEntranceStartScale = 0.96f
private const val StorageCountDurationMillis = 700
private const val CacheSizeBytesPerMegabyte = 1024L * 1024L

private fun cacheSizeMegabytesToBytes(sizeMegabytes: Int): Long = sizeMegabytes.toLong().coerceAtLeast(0L) * CacheSizeBytesPerMegabyte
