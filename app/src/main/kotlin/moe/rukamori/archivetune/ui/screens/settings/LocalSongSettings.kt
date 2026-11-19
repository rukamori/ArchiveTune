/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.localmedia.ScanLocalMusicUseCase
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.viewmodels.LocalSongFolderUiModel
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsAction
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsEvent
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsState
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsUiModel
import moe.rukamori.archivetune.viewmodels.LocalSongsViewModel
import kotlin.math.ceil
import kotlin.math.roundToInt

const val LocalSongSettingsRoute = "settings/local_songs"

@Composable
fun LocalSongSettings(navController: NavController, viewModel: LocalSongsViewModel) {
    val state by viewModel.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onAction = remember(viewModel) { viewModel::onSettingsAction }
    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionPicker = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onAction(LocalSongSettingsAction.PermissionResult(it))
    }
    val includedPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        onAction(LocalSongSettingsAction.FolderSelected(included = true, uri = it))
    }
    val excludedPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
        onAction(LocalSongSettingsAction.FolderSelected(included = false, uri = it))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSettings() }
    LaunchedEffect(viewModel, context, navController, permission) {
        viewModel.settingsEvents.collect { event ->
            try {
                when (event) {
                    LocalSongSettingsEvent.NavigateBack -> navController.navigateUp()
                    LocalSongSettingsEvent.RequestAudioPermission -> permissionPicker.launch(permission)
                    LocalSongSettingsEvent.OpenAppSettings -> context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                    is LocalSongSettingsEvent.PickFolder -> {
                        if (event.included) includedPicker.launch(event.initialUri)
                        else excludedPicker.launch(event.initialUri)
                    }
                }
            } catch (_: ActivityNotFoundException) {
                onAction(LocalSongSettingsAction.ExternalActionFailed)
            } catch (_: SecurityException) {
                onAction(LocalSongSettingsAction.ExternalActionFailed)
            }
        }
    }
    BackHandler(onBack = remember(onAction) { { onAction(LocalSongSettingsAction.Back) } })
    LocalSongSettingsContent(state, onAction)
}

@Composable
private fun LocalSongSettingsContent(state: LocalSongSettingsState, onAction: (LocalSongSettingsAction) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val onBack = remember(onAction) { { onAction(LocalSongSettingsAction.Back) } }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.local_song_settings_title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(stringResource(R.string.local_songs_scan_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back_button_desc))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val insets = LocalPlayerAwareWindowInsets.current
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
            contentAlignment = Alignment.TopCenter,
        ) {
            val model = state.data
            when {
                model != null -> LocalSongSettingsList(state, model, onAction)
                state is LocalSongSettingsState.Error -> LocalSongSettingsFailure(
                    message = state.message,
                    onRetry = remember(onAction) { { onAction(LocalSongSettingsAction.Reload) } },
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LoadingIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun LocalSongSettingsFailure(@StringRes message: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.widthIn(max = 420.dp).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MorphEmblem(
            polygon = MaterialShapes.Diamond,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Icon(painterResource(R.drawable.error), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
        }
        Text(
            stringResource(message),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun LocalSongSettingsList(
    state: LocalSongSettingsState,
    model: LocalSongSettingsUiModel,
    onAction: (LocalSongSettingsAction) -> Unit,
) {
    val onInclude = remember(onAction) { { onAction(LocalSongSettingsAction.PickFolder(included = true)) } }
    val onExclude = remember(onAction) { { onAction(LocalSongSettingsAction.PickFolder(included = false)) } }
    val onAutoScan = remember(onAction) { { enabled: Boolean -> onAction(LocalSongSettingsAction.AutoScanChanged(enabled)) } }
    val onAppSettings = remember(onAction) { { onAction(LocalSongSettingsAction.OpenAppSettings) } }
    val motion = MaterialTheme.motionScheme
    val placement = remember(motion) { motion.defaultSpatialSpec<IntOffset>() }
    val fade = remember(motion) { motion.defaultEffectsSpec<Float>() }
    LazyColumn(
        modifier = Modifier.widthIn(max = SettingsDimensions.HubContentMaxWidth).fillMaxSize(),
        contentPadding = PaddingValues(
            start = SettingsDimensions.ScreenHorizontalPadding,
            end = SettingsDimensions.ScreenHorizontalPadding,
            top = 8.dp,
            bottom = SettingsDimensions.ScreenBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(GroupGap),
    ) {
        item(key = "scan_hero", contentType = "scan_hero") {
            LocalSongScanHero(state, model, onAction)
        }
        item(key = "auto_scan", contentType = "auto_scan") {
            LocalSongAutoScanTile(
                enabled = model.autoScanEnabled,
                onToggle = onAutoScan,
                modifier = Modifier.padding(top = SectionGap),
            )
        }
        item(key = "access_heading", contentType = "section_label") {
            LocalSongSectionLabel(R.string.permission_storage_title)
        }
        item(key = "access_tiles", contentType = "access_tiles") {
            LocalSongAccessTiles(model, onAppSettings)
        }
        item(key = "filters_heading", contentType = "section_label") {
            LocalSongSectionLabel(R.string.local_songs_scan_filters_title)
        }
        item(key = "duration", contentType = "duration") {
            LocalSongDurationPanel(model.minimumDurationSeconds, model.canEdit, onAction)
        }
        item(key = "included_header", contentType = "folder_header") {
            LocalSongFolderGroupHeader(
                included = true,
                count = model.includedFolders.size,
                enabled = model.canEdit,
                onAdd = onInclude,
                modifier = Modifier.padding(top = SectionGap),
            )
        }
        if (model.includedFolders.isEmpty()) {
            item(key = "included_empty", contentType = "folder_empty") {
                LocalSongFolderEmpty(
                    R.string.local_songs_scan_included_folders_empty,
                    Modifier.animateItem(fadeInSpec = fade, placementSpec = placement, fadeOutSpec = fade),
                )
            }
        }
        itemsIndexed(model.includedFolders, key = { _, folder -> "included:${folder.entry}" }, contentType = { _, _ -> "folder" }) { index, folder ->
            LocalSongFolderRow(
                folder = folder,
                last = index == model.includedFolders.lastIndex,
                included = true,
                enabled = model.canEdit,
                onAction = onAction,
                modifier = Modifier.animateItem(fadeInSpec = fade, placementSpec = placement, fadeOutSpec = fade),
            )
        }
        item(key = "excluded_header", contentType = "folder_header") {
            LocalSongFolderGroupHeader(
                included = false,
                count = model.excludedFolders.size,
                enabled = model.canEdit,
                onAdd = onExclude,
                modifier = Modifier.padding(top = SectionGap),
            )
        }
        if (model.excludedFolders.isEmpty()) {
            item(key = "excluded_empty", contentType = "folder_empty") {
                LocalSongFolderEmpty(
                    R.string.local_songs_scan_folders_empty,
                    Modifier.animateItem(fadeInSpec = fade, placementSpec = placement, fadeOutSpec = fade),
                )
            }
        }
        itemsIndexed(model.excludedFolders, key = { _, folder -> "excluded:${folder.entry}" }, contentType = { _, _ -> "folder" }) { index, folder ->
            LocalSongFolderRow(
                folder = folder,
                last = index == model.excludedFolders.lastIndex,
                included = false,
                enabled = model.canEdit,
                onAction = onAction,
                modifier = Modifier.animateItem(fadeInSpec = fade, placementSpec = placement, fadeOutSpec = fade),
            )
        }
        item(key = "settings_note", contentType = "settings_note") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = SectionGap),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.info), null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.local_songs_scan_filters_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class ScanStage { Ready, Scanning, Done, Empty, Attention }

@Composable
private fun LocalSongScanHero(
    state: LocalSongSettingsState,
    model: LocalSongSettingsUiModel,
    onAction: (LocalSongSettingsAction) -> Unit,
) {
    val error = (state as? LocalSongSettingsState.Error)?.message
    val stage = when {
        model.isScanning -> ScanStage.Scanning
        error != null -> ScanStage.Attention
        state is LocalSongSettingsState.Empty -> ScanStage.Empty
        model.summary != null -> ScanStage.Done
        else -> ScanStage.Ready
    }
    val title = stringResource(when (stage) {
        ScanStage.Scanning -> R.string.scanning_device
        ScanStage.Attention -> R.string.local_song_settings_attention
        ScanStage.Empty -> R.string.local_songs_empty_title
        ScanStage.Done -> R.string.local_songs_latest_scan
        ScanStage.Ready -> R.string.local_song_settings_ready
    })
    val summary = model.summary
    val message = when {
        stage == ScanStage.Scanning -> stringResource(R.string.local_songs_scan_subtitle)
        error != null -> stringResource(error)
        summary?.metadataLookupFailed == true -> stringResource(R.string.local_songs_metadata_unavailable, summary.scannedSongs)
        summary != null -> stringResource(R.string.local_songs_scan_summary, summary.scannedSongs, summary.removedSongs)
        else -> stringResource(R.string.local_songs_ready_desc)
    }
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val effects = motion.defaultEffectsSpec<Color>()
    val container by animateColorAsState(when (stage) {
        ScanStage.Scanning -> colors.secondaryContainer
        ScanStage.Attention -> colors.errorContainer
        else -> colors.primaryContainer
    }, effects, label = "heroContainer")
    val content by animateColorAsState(when (stage) {
        ScanStage.Scanning -> colors.onSecondaryContainer
        ScanStage.Attention -> colors.onErrorContainer
        else -> colors.onPrimaryContainer
    }, effects, label = "heroContent")
    val emblem by animateColorAsState(when (stage) {
        ScanStage.Scanning -> colors.secondary
        ScanStage.Attention -> colors.error
        else -> colors.primary
    }, effects, label = "heroEmblem")
    val emblemContent = when (stage) {
        ScanStage.Scanning -> colors.onSecondary
        ScanStage.Attention -> colors.onError
        else -> colors.onPrimary
    }
    val rotation = rememberScanRotation(stage == ScanStage.Scanning)
    val fade = motion.defaultEffectsSpec<Float>()
    val fastFade = motion.fastEffectsSpec<Float>()
    val onScan = remember(onAction) { { onAction(LocalSongSettingsAction.Scan) } }
    val onCancel = remember(onAction) { { onAction(LocalSongSettingsAction.CancelScan) } }
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(motion.defaultSpatialSpec()),
        shape = HeroShape,
        color = container,
        contentColor = content,
    ) {
        Box {
            Box(Modifier.matchParentSize()) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 64.dp, y = (-72).dp).requiredSize(220.dp)
                        .graphicsLayer { rotationZ = -rotation.value * 0.35f }
                        .background(content.copy(alpha = 0.07f), MaterialShapes.Cookie12Sided.toShape()),
                )
            }
            Column(Modifier.padding(HeroPadding), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    MorphEmblem(
                        polygon = stage.polygon(),
                        color = emblem,
                        modifier = Modifier.size(84.dp),
                        rotation = { rotation.value },
                    ) {
                        AnimatedContent(
                            targetState = stage.icon(),
                            transitionSpec = {
                                (fadeIn(fade) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                                    (fadeOut(fastFade) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f))
                            },
                            label = "heroIcon",
                        ) { icon ->
                            Icon(painterResource(icon), null, Modifier.size(34.dp), tint = emblemContent)
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AnimatedContent(
                            targetState = title,
                            transitionSpec = {
                                (fadeIn(fade) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 }) togetherWith
                                    (fadeOut(fastFade) + slideOutVertically(motion.defaultSpatialSpec()) { -it / 2 })
                            },
                            label = "heroTitle",
                        ) { Text(it, style = MaterialTheme.typography.headlineSmallEmphasized) }
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                AnimatedVisibility(
                    visible = summary != null && stage != ScanStage.Scanning,
                    enter = fadeIn(fade) + expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(fastFade) + shrinkVertically(motion.defaultSpatialSpec()),
                ) {
                    if (summary != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LocalSongScanStat(summary.scannedSongs, R.string.local_song_settings_found, StatStartShape, Modifier.weight(1f))
                            LocalSongScanStat(summary.removedSongs, R.string.local_song_settings_removed, StatEndShape, Modifier.weight(1f))
                        }
                    }
                }
                if (stage == ScanStage.Empty) {
                    Text(stringResource(R.string.local_song_settings_empty_hint), style = MaterialTheme.typography.bodyMedium)
                }
                AnimatedVisibility(
                    visible = stage == ScanStage.Scanning,
                    enter = fadeIn(fade) + expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(fastFade) + shrinkVertically(motion.defaultSpatialSpec()),
                ) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = emblem,
                        trackColor = content.copy(alpha = 0.16f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onScan,
                        shapes = ButtonDefaults.shapes(),
                        enabled = model.canEdit,
                        modifier = Modifier.weight(1f).heightIn(min = ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight, hasStartIcon = true),
                    ) {
                        Icon(painterResource(R.drawable.sync), null, Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight)))
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
                        Text(
                            stringResource(when {
                                model.isScanning -> R.string.scanning_device
                                model.isSaving -> R.string.local_song_settings_saving
                                model.requiresMediaPermission && !model.hasMediaPermission -> R.string.allow
                                else -> R.string.scan_device
                            }),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    AnimatedVisibility(
                        visible = model.isScanning,
                        enter = fadeIn(fade) + expandHorizontally(motion.defaultSpatialSpec()),
                        exit = fadeOut(fastFade) + shrinkHorizontally(motion.defaultSpatialSpec()),
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                        ) { Text(stringResource(R.string.cancel), color = content) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalSongScanStat(value: Int, @StringRes label: Int, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val direction = if (targetState >= initialState) 1 else -1
                (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it * direction / 2 }) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.defaultSpatialSpec()) { -it * direction / 2 })
            },
            label = "scanStat",
        ) { count ->
            Text(
                count.toString(),
                style = MaterialTheme.typography.displaySmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LocalSongAutoScanTile(enabled: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(if (enabled) 32.dp else 20.dp, motion.fastSpatialSpec(), label = "autoScanCorner")
    val container by animateColorAsState(if (enabled) colors.tertiaryContainer else colors.surfaceContainerHigh, motion.defaultEffectsSpec(), label = "autoScanContainer")
    val content by animateColorAsState(if (enabled) colors.onTertiaryContainer else colors.onSurface, motion.defaultEffectsSpec(), label = "autoScanContent")
    val emblem by animateColorAsState(if (enabled) colors.tertiary else colors.secondaryContainer, motion.defaultEffectsSpec(), label = "autoScanEmblem")
    val emblemContent = if (enabled) colors.onTertiary else colors.onSecondaryContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onToggle)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MorphEmblem(
                polygon = if (enabled) MaterialShapes.Cookie6Sided else MaterialShapes.Circle,
                color = emblem,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(painterResource(R.drawable.auto_awesome), null, Modifier.size(22.dp), tint = emblemContent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.local_song_settings_auto_scan), style = MaterialTheme.typography.titleMediumEmphasized)
                AnimatedContent(
                    targetState = enabled,
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "autoScanDescription",
                ) { on ->
                    Text(
                        stringResource(if (on) R.string.local_song_settings_auto_scan_on else R.string.local_song_settings_auto_scan_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.8f),
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = null,
                thumbContent = {
                    Icon(
                        painterResource(if (enabled) R.drawable.check else R.drawable.close),
                        null,
                        Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
private fun LocalSongSectionLabel(@StringRes text: Int) {
    Text(
        stringResource(text),
        modifier = Modifier.padding(start = 8.dp, top = SectionGap + 8.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LocalSongAccessTiles(model: LocalSongSettingsUiModel, onAppSettings: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val permissionMissing = model.requiresMediaPermission && !model.hasMediaPermission
    val permissionContainer = when {
        model.hasMediaPermission -> colors.secondaryContainer
        permissionMissing -> colors.errorContainer
        else -> colors.surfaceContainerHigh
    }
    val permissionContent = when {
        model.hasMediaPermission -> colors.onSecondaryContainer
        permissionMissing -> colors.onErrorContainer
        else -> colors.onSurface
    }
    val permission: @Composable (Modifier, RoundedCornerShape) -> Unit = { modifier, shape ->
        LocalSongAccessTile(
            icon = R.drawable.security,
            polygon = if (model.hasMediaPermission) MaterialShapes.Clover4Leaf else MaterialShapes.Pentagon,
            title = stringResource(R.string.local_song_settings_audio_permission),
            badge = stringResource(if (model.hasMediaPermission) R.string.permission_status_allowed else R.string.not_allowed),
            body = stringResource(if (model.hasMediaPermission) R.string.local_song_settings_permission_granted else R.string.local_song_settings_permission_optional),
            container = permissionContainer,
            content = permissionContent,
            shape = shape,
            onClick = onAppSettings,
            modifier = modifier,
        )
    }
    val folderCount = model.includedFolders.size
    val scope: @Composable (Modifier, RoundedCornerShape) -> Unit = { modifier, shape ->
        LocalSongAccessTile(
            icon = R.drawable.storage,
            polygon = if (folderCount == 0) MaterialShapes.Arch else MaterialShapes.Cookie9Sided,
            title = stringResource(R.string.local_song_settings_scan_scope),
            badge = if (folderCount == 0) null else pluralStringResource(R.plurals.local_song_settings_folder_count, folderCount, folderCount),
            body = stringResource(if (folderCount == 0) R.string.local_song_settings_scope_device else R.string.local_song_settings_scope_folders),
            container = colors.surfaceContainerHigh,
            content = colors.onSurface,
            shape = shape,
            onClick = null,
            modifier = modifier,
        )
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= AccessTwoPaneWidth) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GroupGap)) {
                permission(Modifier.weight(1f).fillMaxHeight(), AccessStartShape)
                scope(Modifier.weight(1f).fillMaxHeight(), AccessEndShape)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
                permission(Modifier.fillMaxWidth(), GroupTopShape)
                scope(Modifier.fillMaxWidth(), GroupBottomShape)
            }
        }
    }
}

@Composable
private fun LocalSongAccessTile(
    @DrawableRes icon: Int,
    polygon: RoundedPolygon,
    title: String,
    badge: String?,
    body: String,
    container: Color,
    content: Color,
    shape: RoundedCornerShape,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val animatedContainer by animateColorAsState(container, motion.defaultEffectsSpec(), label = "accessContainer")
    val animatedContent by animateColorAsState(content, motion.defaultEffectsSpec(), label = "accessContent")
    val tileContent: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MorphEmblem(polygon = polygon, color = animatedContent.copy(alpha = 0.12f), modifier = Modifier.size(44.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(22.dp), tint = animatedContent)
                }
                Spacer(Modifier.weight(1f))
                if (badge != null) {
                    Text(
                        badge,
                        modifier = Modifier.background(animatedContent.copy(alpha = 0.1f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                    )
                }
            }
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(body, style = MaterialTheme.typography.bodySmall, color = animatedContent.copy(alpha = 0.8f))
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = animatedContainer, contentColor = animatedContent, content = tileContent)
    } else {
        Surface(modifier = modifier, shape = shape, color = animatedContainer, contentColor = animatedContent, content = tileContent)
    }
}

@Composable
private fun LocalSongDurationPanel(
    seconds: Int,
    enabled: Boolean,
    onAction: (LocalSongSettingsAction) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val durationLabel = if (seconds == 0) stringResource(R.string.dark_theme_off)
    else pluralStringResource(R.plurals.seconds, seconds, seconds)
    val maximum = ScanLocalMusicUseCase.MaximumMinimumDurationSeconds
    val label = stringResource(R.string.local_songs_scan_duration_title)
    val onValueChange = remember(onAction) { { value: Float -> onAction(LocalSongSettingsAction.DurationChanged(value.roundToInt())) } }
    val onValueFinished = remember(onAction) { { onAction(LocalSongSettingsAction.SaveDuration) } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = colors.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MorphEmblem(
                    polygon = if (seconds == 0) MaterialShapes.Circle else MaterialShapes.Sunny,
                    color = if (seconds == 0) colors.surfaceContainerHighest else colors.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.timer), null, Modifier.size(22.dp),
                        tint = if (seconds == 0) colors.onSurfaceVariant else colors.onPrimaryContainer,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMediumEmphasized, color = colors.onSurface)
                    Text(stringResource(R.string.local_songs_scan_duration_desc), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            AnimatedContent(
                targetState = seconds,
                transitionSpec = {
                    val direction = if (targetState >= initialState) 1 else -1
                    (fadeIn(motion.fastEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { it * direction / 2 }) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it * direction / 2 })
                },
                modifier = Modifier.padding(start = 4.dp),
                label = "durationValue",
            ) { value ->
                Text(
                    if (value == 0) stringResource(R.string.dark_theme_off) else pluralStringResource(R.plurals.seconds, value, value),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = if (value == 0) colors.onSurfaceVariant else colors.primary,
                    maxLines = 1,
                )
            }
            Slider(
                value = seconds.toFloat(),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueFinished,
                valueRange = 0f..maximum.toFloat(),
                steps = 11,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label; stateDescription = durationLabel },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(stringResource(R.string.dark_theme_off), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(pluralStringResource(R.plurals.seconds, maximum, maximum), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LocalSongFolderGroupHeader(
    included: Boolean,
    count: Int,
    enabled: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GroupTopShape,
        color = colors.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MorphEmblem(
                    polygon = if (included) MaterialShapes.Clover4Leaf else MaterialShapes.Slanted,
                    color = if (included) colors.primaryContainer else colors.tertiaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painterResource(if (included) R.drawable.snippet_folder else R.drawable.block), null, Modifier.size(22.dp),
                        tint = if (included) colors.onPrimaryContainer else colors.onTertiaryContainer,
                    )
                }
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(if (included) R.string.local_songs_scan_included_folders_title else R.string.local_songs_scan_folders_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colors.onSurface,
                    )
                    Text(
                        pluralStringResource(R.plurals.local_song_settings_folder_count, count, count),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(if (included) R.string.local_songs_scan_included_folders_desc else R.string.local_songs_scan_folders_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = onAdd,
                enabled = enabled,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
            ) {
                Icon(painterResource(R.drawable.add), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(if (included) R.string.local_songs_scan_included_folders_add else R.string.local_songs_scan_folders_add))
            }
        }
    }
}

@Composable
private fun LocalSongFolderEmpty(@StringRes text: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GroupBottomShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            stringResource(text),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LocalSongFolderRow(
    folder: LocalSongFolderUiModel,
    last: Boolean,
    included: Boolean,
    enabled: Boolean,
    onAction: (LocalSongSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val onRemove = remember(folder.entry, included, onAction) { { onAction(LocalSongSettingsAction.RemoveFolder(folder.entry, included)) } }
    val onRepair = remember(folder.entry, onAction) {
        { onAction(LocalSongSettingsAction.PickFolder(included = true, initialUri = Uri.parse(folder.entry).takeIf { it.scheme == "content" })) }
    }
    val location = when (folder.volume) {
        "primary" -> stringResource(R.string.local_song_settings_internal_storage)
        null -> stringResource(R.string.local_song_settings_saved_path)
        else -> folder.volume
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = if (last) GroupBottomShape else GroupMiddleShape,
        color = colors.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(
                    if (folder.needsAccess) colors.errorContainer else colors.secondaryContainer,
                    FolderIconShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (folder.needsAccess) R.drawable.lock else R.drawable.snippet_folder), null, Modifier.size(20.dp),
                    tint = if (folder.needsAccess) colors.onErrorContainer else colors.onSecondaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(folder.path, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(location, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (folder.needsAccess) {
                    Text(
                        stringResource(R.string.local_song_settings_folder_access_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(onClick = onRepair, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.local_song_settings_choose_again))
                    }
                } else if (included) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.check), null, Modifier.size(16.dp), tint = colors.tertiary)
                        Text(stringResource(R.string.local_song_settings_folder_access_saved), style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
                    }
                }
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(painterResource(R.drawable.close), stringResource(R.string.modules_remove))
            }
        }
    }
}

@Composable
private fun MorphEmblem(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit,
) {
    val morphSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    var start by remember { mutableStateOf(polygon) }
    var end by remember { mutableStateOf(polygon) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(polygon) {
        if (polygon != end) {
            start = end
            end = polygon
            progress.snapTo(0f)
            progress.animateTo(1f, morphSpec)
        }
    }
    val morph = remember(start, end) { Morph(start, end) }
    Box(
        modifier = modifier.drawBehind {
            rotate(rotation()) {
                drawOutline(EditorialMorphShape(morph, progress.value).createOutline(size, layoutDirection, this), color)
            }
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
private fun rememberScanRotation(scanning: Boolean): Animatable<Float, AnimationVector1D> {
    val rotation = remember { Animatable(0f) }
    val settleSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(scanning) {
        if (scanning) {
            if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
            while (true) {
                rotation.snapTo(rotation.value % 360f)
                rotation.animateTo(rotation.value + 360f, tween(ScanSpinMillis, easing = LinearEasing))
            }
        } else {
            val rest = ceil(rotation.value / 90f) * 90f
            rotation.animateTo(rest, settleSpec)
            rotation.snapTo(rest % 360f)
        }
    }
    return rotation
}

private fun ScanStage.polygon(): RoundedPolygon = when (this) {
    ScanStage.Ready -> MaterialShapes.Cookie9Sided
    ScanStage.Scanning -> MaterialShapes.SoftBurst
    ScanStage.Done -> MaterialShapes.Sunny
    ScanStage.Empty -> MaterialShapes.Ghostish
    ScanStage.Attention -> MaterialShapes.Diamond
}

@DrawableRes
private fun ScanStage.icon(): Int = when (this) {
    ScanStage.Ready -> R.drawable.library_music
    ScanStage.Scanning -> R.drawable.sync
    ScanStage.Done -> R.drawable.done
    ScanStage.Empty -> R.drawable.music_note
    ScanStage.Attention -> R.drawable.error
}

private val GroupGap: Dp = 4.dp
private val SectionGap: Dp = 12.dp
private val HeroPadding: Dp = 24.dp
private val AccessTwoPaneWidth: Dp = 560.dp
private const val ScanSpinMillis = 4_800
private val HeroShape = RoundedCornerShape(40.dp)
private val PanelShape = RoundedCornerShape(28.dp)
private val StatStartShape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp)
private val StatEndShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp)
private val AccessStartShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 12.dp, bottomEnd = 12.dp)
private val AccessEndShape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 28.dp, bottomEnd = 28.dp)
private val GroupTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
private val GroupMiddleShape = RoundedCornerShape(8.dp)
private val GroupBottomShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
private val FolderIconShape = RoundedCornerShape(14.dp)
