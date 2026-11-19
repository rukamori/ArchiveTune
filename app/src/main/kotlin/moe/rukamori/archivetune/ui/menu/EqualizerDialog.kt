/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.equalizer.EqualizerControlMode
import moe.rukamori.archivetune.equalizer.EqualizerTone
import moe.rukamori.archivetune.viewmodels.EqualizerEffect
import moe.rukamori.archivetune.viewmodels.EqualizerPresetUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerProfileUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerProfileUiModels
import moe.rukamori.archivetune.viewmodels.EqualizerScreenState
import moe.rukamori.archivetune.viewmodels.EqualizerUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerViewModel

@Stable
private class EqualizerActions(
    val onOpenSystemEqualizer: () -> Unit,
    val onEnabledChange: (Boolean) -> Unit,
    val onModeChange: (EqualizerControlMode) -> Unit,
    val onPresetClick: (String) -> Unit,
    val onToneValueChange: (EqualizerTone, Int) -> Unit,
    val onToneValueChangeFinished: (EqualizerTone) -> Unit,
    val onBandValueChange: (Int, Int) -> Unit,
    val onBandValueChangeFinished: () -> Unit,
    val onResetBands: () -> Unit,
    val onOutputGainEnabledChange: (Boolean) -> Unit,
    val onOutputGainValueChange: (Int) -> Unit,
    val onOutputGainValueChangeFinished: () -> Unit,
    val onBassBoostEnabledChange: (Boolean) -> Unit,
    val onBassBoostValueChange: (Int) -> Unit,
    val onBassBoostValueChangeFinished: () -> Unit,
    val onVirtualizerEnabledChange: (Boolean) -> Unit,
    val onVirtualizerValueChange: (Int) -> Unit,
    val onVirtualizerValueChangeFinished: () -> Unit,
    val onAutoHeadroomEnabledChange: (Boolean) -> Unit,
    val onShowSaveProfile: () -> Unit,
    val onProfileNameChange: (String) -> Unit,
    val onSaveProfile: () -> Unit,
    val onDismissSaveProfile: () -> Unit,
    val onShowManageProfiles: () -> Unit,
    val onDismissManageProfiles: () -> Unit,
    val onApplyProfile: (String) -> Unit,
    val onDeleteProfile: (String) -> Unit,
    val onImportProfiles: () -> Unit,
    val onExportProfile: (String) -> Unit,
)

@Composable
fun EqualizerDialog(
    onDismiss: () -> Unit,
    openSystemEqualizer: () -> Unit,
    viewModel: EqualizerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importProfiles)
        }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let(viewModel::exportProfile)
        }

    LaunchedEffect(viewModel, context, importLauncher, exportLauncher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EqualizerEffect.ShowMessage -> {
                    val message =
                        effect.quantity?.let { context.getString(effect.messageResId, it) }
                            ?: context.getString(effect.messageResId)
                    snackbarHostState.showSnackbar(message)
                }

                EqualizerEffect.OpenImportDocument -> {
                    importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                }

                is EqualizerEffect.CreateExportDocument -> {
                    exportLauncher.launch(effect.suggestedName)
                }
            }
        }
    }

    val actions =
        remember(viewModel, openSystemEqualizer) {
            EqualizerActions(
                onOpenSystemEqualizer = openSystemEqualizer,
                onEnabledChange = viewModel::setEnabled,
                onModeChange = viewModel::setControlMode,
                onPresetClick = viewModel::applyPreset,
                onToneValueChange = viewModel::updateToneDraft,
                onToneValueChangeFinished = viewModel::commitTone,
                onBandValueChange = viewModel::updateBandDraft,
                onBandValueChangeFinished = viewModel::commitBands,
                onResetBands = viewModel::resetBands,
                onOutputGainEnabledChange = viewModel::setOutputGainEnabled,
                onOutputGainValueChange = viewModel::updateOutputGainDraft,
                onOutputGainValueChangeFinished = viewModel::commitOutputGain,
                onBassBoostEnabledChange = viewModel::setBassBoostEnabled,
                onBassBoostValueChange = viewModel::updateBassBoostDraft,
                onBassBoostValueChangeFinished = viewModel::commitBassBoost,
                onVirtualizerEnabledChange = viewModel::setVirtualizerEnabled,
                onVirtualizerValueChange = viewModel::updateVirtualizerDraft,
                onVirtualizerValueChangeFinished = viewModel::commitVirtualizer,
                onAutoHeadroomEnabledChange = viewModel::setAutoHeadroomEnabled,
                onShowSaveProfile = viewModel::showSaveProfileDialog,
                onProfileNameChange = viewModel::updateProfileName,
                onSaveProfile = viewModel::saveProfile,
                onDismissSaveProfile = viewModel::dismissSaveProfileDialog,
                onShowManageProfiles = viewModel::showManageProfiles,
                onDismissManageProfiles = viewModel::dismissManageProfiles,
                onApplyProfile = viewModel::applyProfile,
                onDeleteProfile = viewModel::deleteProfile,
                onImportProfiles = viewModel::requestImport,
                onExportProfile = viewModel::requestExport,
            )
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        EqualizerScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onDismiss = onDismiss,
            actions = actions,
        )
    }
}

@Composable
private fun EqualizerScreen(
    state: EqualizerScreenState,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    actions: EqualizerActions,
) {
    val motion = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = stringResource(R.string.equalizer)) },
                subtitle = { Text(text = stringResource(R.string.eq_screen_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                        Icon(painter = painterResource(R.drawable.close), contentDescription = stringResource(R.string.eq_close))
                    }
                },
                actions = {
                    IconButton(onClick = actions.onOpenSystemEqualizer, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painter = painterResource(R.drawable.tune),
                            contentDescription = stringResource(R.string.eq_open_system_equalizer),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { contentPadding ->
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "equalizerState",
        ) { screenState ->
            when (screenState) {
                EqualizerScreenState.Loading -> {
                    EqualizerLoading(contentPadding)
                }

                EqualizerScreenState.Empty -> {
                    EqualizerMessage(
                        contentPadding = contentPadding,
                        message = stringResource(R.string.eq_waiting_for_audio_session),
                        onOpenSystemEqualizer = actions.onOpenSystemEqualizer,
                    )
                }

                is EqualizerScreenState.Error -> {
                    EqualizerMessage(
                        contentPadding = contentPadding,
                        message = stringResource(screenState.messageResId),
                        onOpenSystemEqualizer = actions.onOpenSystemEqualizer,
                    )
                }

                is EqualizerScreenState.Success -> {
                    EqualizerContent(model = screenState.model, contentPadding = contentPadding, actions = actions)
                }
            }
        }
    }

    val model = (state as? EqualizerScreenState.Success)?.model
    if (model?.saveProfileDialog?.visible == true) {
        SaveProfileDialog(
            name = model.saveProfileDialog.name,
            onNameChange = actions.onProfileNameChange,
            onSave = actions.onSaveProfile,
            onDismiss = actions.onDismissSaveProfile,
        )
    }
    if (model?.manageProfilesVisible == true) {
        ManageProfilesDialog(
            profiles = model.profiles,
            onApply = actions.onApplyProfile,
            onDelete = actions.onDeleteProfile,
            onExport = actions.onExportProfile,
            onDismiss = actions.onDismissManageProfiles,
        )
    }
}

@Composable
private fun EqualizerContent(
    model: EqualizerUiModel,
    contentPadding: PaddingValues,
    actions: EqualizerActions,
) {
    val choreography = remember { EqualizerEntranceChoreography() }
    var activeBand by remember { mutableStateOf<Int?>(null) }
    val onBandActiveChange: (Int, Boolean) -> Unit = { index, active ->
        activeBand = if (active) index else activeBand.takeUnless { it == index }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= ExpandedLayoutMinWidth) {
            ExpandedEqualizerLayout(model, contentPadding, actions, choreography, activeBand, onBandActiveChange)
        } else {
            CompactEqualizerLayout(model, contentPadding, actions, choreography, activeBand, onBandActiveChange)
        }
    }
}

@Composable
private fun CompactEqualizerLayout(
    model: EqualizerUiModel,
    contentPadding: PaddingValues,
    actions: EqualizerActions,
    choreography: EqualizerEntranceChoreography,
    activeBand: Int?,
    onBandActiveChange: (Int, Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val advanced = model.controlMode == EqualizerControlMode.ADVANCED
    val itemModifier = Modifier.widthIn(max = CompactContentMaxWidth).fillMaxWidth()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 32.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "power", contentType = "power") {
            PowerHero(model, actions.onEnabledChange, itemModifier.equalizerEntrance(choreography, "power", 0))
        }
        item(key = "mode", contentType = "mode") {
            ModeSwitcher(model.controlMode, actions.onModeChange, itemModifier.equalizerEntrance(choreography, "mode", 1))
        }
        item(key = "presets", contentType = "presets") {
            PresetsSection(model, actions.onPresetClick, itemModifier.equalizerEntrance(choreography, "presets", 2))
        }
        item(key = "stage", contentType = "stage") {
            SoundStage(
                model = model,
                activeBand = activeBand,
                onBandActiveChange = onBandActiveChange,
                actions = actions,
                curveHeight = 148.dp,
                faderHeight = 208.dp,
                modifier = itemModifier.equalizerEntrance(choreography, "stage", 3),
            )
        }
        if (advanced) {
            item(key = "effects", contentType = "effects") {
                EffectsSection(
                    model,
                    actions,
                    itemModifier
                        .animateItem(fadeInSpec = motion.defaultEffectsSpec(), placementSpec = motion.defaultSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec())
                        .equalizerEntrance(choreography, "effects", 0),
                )
            }
            item(key = "signal", contentType = "signal") {
                SignalSection(
                    model,
                    actions,
                    itemModifier
                        .animateItem(fadeInSpec = motion.defaultEffectsSpec(), placementSpec = motion.defaultSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec())
                        .equalizerEntrance(choreography, "signal", 1),
                )
            }
            item(key = "profiles", contentType = "profiles") {
                ProfilesSection(
                    model,
                    actions,
                    itemModifier
                        .animateItem(fadeInSpec = motion.defaultEffectsSpec(), placementSpec = motion.defaultSpatialSpec(), fadeOutSpec = motion.fastEffectsSpec())
                        .equalizerEntrance(choreography, "profiles", 2),
                )
            }
        }
    }
}

@Composable
private fun ExpandedEqualizerLayout(
    model: EqualizerUiModel,
    contentPadding: PaddingValues,
    actions: EqualizerActions,
    choreography: EqualizerEntranceChoreography,
    activeBand: Int?,
    onBandActiveChange: (Int, Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val columnPadding =
        Modifier.padding(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        )
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState()).then(columnPadding),
        ) {
            SoundStage(
                model = model,
                activeBand = activeBand,
                onBandActiveChange = onBandActiveChange,
                actions = actions,
                curveHeight = 220.dp,
                faderHeight = 280.dp,
                modifier = Modifier.fillMaxWidth().equalizerEntrance(choreography, "stage", 0),
            )
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).then(columnPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PowerHero(model, actions.onEnabledChange, Modifier.fillMaxWidth().equalizerEntrance(choreography, "power", 1))
            ModeSwitcher(model.controlMode, actions.onModeChange, Modifier.fillMaxWidth().equalizerEntrance(choreography, "mode", 2))
            PresetsSection(model, actions.onPresetClick, Modifier.fillMaxWidth().equalizerEntrance(choreography, "presets", 3))
            AnimatedVisibility(
                visible = model.controlMode == EqualizerControlMode.ADVANCED,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    EffectsSection(model, actions, Modifier.fillMaxWidth())
                    SignalSection(model, actions, Modifier.fillMaxWidth())
                    ProfilesSection(model, actions, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PowerHero(
    model: EqualizerUiModel,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val enabled = model.enabled
    val containerColor by animateColorAsState(
        if (enabled) colors.primaryContainer else colors.surfaceContainerHigh,
        motion.defaultEffectsSpec(),
        label = "heroContainer",
    )
    val contentColor by animateColorAsState(
        if (enabled) colors.onPrimaryContainer else colors.onSurface,
        motion.defaultEffectsSpec(),
        label = "heroContent",
    )
    val activeSound = activeSoundLabel(model)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EqualizerPowerBadge(active = enabled, size = 64.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AnimatedContent(
                    targetState = enabled,
                    transitionSpec = {
                        (slideInVertically(motion.defaultSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                            (slideOutVertically(motion.defaultSpatialSpec()) { -it / 2 } + fadeOut(motion.fastEffectsSpec()))
                    },
                    label = "heroStatus",
                ) { on ->
                    Text(
                        text = stringResource(if (on) R.string.eq_sound_shaping_on else R.string.eq_sound_shaping_off),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                }
                AnimatedContent(
                    targetState = activeSound,
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "heroActiveSound",
                ) { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.eq_enable_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun ModeSwitcher(
    selectedMode: EqualizerControlMode,
    onModeChange: (EqualizerControlMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val modes = EqualizerControlMode.entries
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            modes.forEachIndexed { index, mode ->
                val checked = selectedMode == mode
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { if (!checked) onModeChange(mode) },
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    modifier = Modifier.weight(1f).heightIn(min = ModeButtonHeight).semantics { role = Role.RadioButton },
                ) {
                    Icon(
                        painter = painterResource(if (mode == EqualizerControlMode.BASIC) R.drawable.tune else R.drawable.equalizer),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(if (mode == EqualizerControlMode.BASIC) R.string.eq_basic else R.string.eq_advanced),
                        maxLines = 1,
                    )
                }
            }
        }
        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "modeDescription",
        ) { mode ->
            Text(
                text = stringResource(if (mode == EqualizerControlMode.BASIC) R.string.eq_basic_description else R.string.eq_advanced_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PresetsSection(
    model: EqualizerUiModel,
    onPresetClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(title = stringResource(R.string.eq_presets), supporting = stringResource(R.string.eq_presets_description))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(count = model.presets.size, key = { model.presets[it].id }, contentType = { "preset" }) { index ->
                val preset = model.presets[index]
                ToggleButton(
                    checked = preset.isSelected,
                    onCheckedChange = { if (!preset.isSelected) onPresetClick(preset.id) },
                    enabled = model.enabled,
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) {
                    AnimatedVisibility(
                        visible = preset.isSelected,
                        enter = fadeIn(motion.fastEffectsSpec()) + expandHorizontally(motion.fastSpatialSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.fastSpatialSpec()),
                    ) {
                        Row {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        }
                    }
                    Text(text = presetLabel(preset, index), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SoundStage(
    model: EqualizerUiModel,
    activeBand: Int?,
    onBandActiveChange: (Int, Boolean) -> Unit,
    actions: EqualizerActions,
    curveHeight: Dp,
    faderHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    val levels = remember(model.bands) { List(model.bands.size) { model.bands[it].levelMb } }
    val topLabel = formatDecibels(model.maximumBandLevelMb)
    val bottomLabel = formatDecibels(model.minimumBandLevelMb)
    val curveDescription = stringResource(R.string.eq_response_curve)
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLargeIncreased, color = surfaceColor) {
        Column(modifier = Modifier.padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = model.controlMode,
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "stageHeading",
                    modifier = Modifier.weight(1f),
                ) { mode ->
                    SectionHeading(
                        title = stringResource(if (mode == EqualizerControlMode.BASIC) R.string.eq_tone else R.string.eq_bands),
                        supporting =
                            stringResource(
                                if (mode == EqualizerControlMode.BASIC) R.string.eq_tone_description else R.string.eq_bands_description,
                            ),
                    )
                }
                TextButton(onClick = actions.onResetBands, enabled = model.enabled, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.reset))
                }
            }
            AnimatedVisibility(
                visible = !model.enabled,
                enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
            ) {
                Text(
                    text = stringResource(R.string.eq_turn_on_to_adjust),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            AnimatedContent(
                targetState = model.controlMode,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)) togetherWith
                        fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
                },
                label = "stageControls",
            ) { mode ->
                when (mode) {
                    EqualizerControlMode.BASIC -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            EqualizerResponseCurve(
                                levelsMb = levels,
                                minimumMb = model.minimumBandLevelMb,
                                maximumMb = model.maximumBandLevelMb,
                                enabled = model.enabled,
                                highlightedIndex = null,
                                topLabel = topLabel,
                                bottomLabel = bottomLabel,
                                contentDescription = curveDescription,
                                surfaceColor = surfaceColor,
                                modifier = Modifier.fillMaxWidth().height(curveHeight),
                            )
                            ToneFaders(model, faderHeight, actions)
                        }
                    }

                    EqualizerControlMode.ADVANCED -> {
                        BandFaders(
                            model = model,
                            levels = levels,
                            activeBand = activeBand,
                            onBandActiveChange = onBandActiveChange,
                            curveHeight = curveHeight,
                            faderHeight = faderHeight,
                            topLabel = topLabel,
                            bottomLabel = bottomLabel,
                            curveDescription = curveDescription,
                            surfaceColor = surfaceColor,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneFaders(
    model: EqualizerUiModel,
    faderHeight: Dp,
    actions: EqualizerActions,
) {
    val resetLabel = stringResource(R.string.eq_reset_to_zero)
    Row(modifier = Modifier.fillMaxWidth()) {
        repeat(model.tones.size) { index ->
            val tone = model.tones[index]
            FaderColumn(
                label =
                    stringResource(
                        when (tone.tone) {
                            EqualizerTone.BASS -> R.string.eq_bass
                            EqualizerTone.MIDRANGE -> R.string.eq_midrange
                            EqualizerTone.TREBLE -> R.string.eq_treble
                        },
                    ),
                value = tone.levelMb,
                valueRange = model.minimumBandLevelMb..model.maximumBandLevelMb,
                valueText = formatCompactDecibels(tone.levelMb),
                valueDescription = formatDecibels(tone.levelMb),
                enabled = model.enabled,
                faderWidth = ToneFaderWidth,
                faderHeight = faderHeight,
                prominent = true,
                resetLabel = resetLabel,
                onValueChange = { actions.onToneValueChange(tone.tone, it) },
                onValueChangeFinished = { actions.onToneValueChangeFinished(tone.tone) },
                onActiveChange = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BandFaders(
    model: EqualizerUiModel,
    levels: List<Int>,
    activeBand: Int?,
    onBandActiveChange: (Int, Boolean) -> Unit,
    curveHeight: Dp,
    faderHeight: Dp,
    topLabel: String,
    bottomLabel: String,
    curveDescription: String,
    surfaceColor: Color,
    actions: EqualizerActions,
) {
    val resetLabel = stringResource(R.string.eq_reset_to_zero)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val count = model.bands.size.coerceAtLeast(1)
        val available = maxWidth - BandStageHorizontalPadding * 2
        val columnWidth = maxOf(BandColumnMinWidth, available / count)
        val contentWidth = columnWidth * count
        val scrollable = BandColumnMinWidth * count > available
        val scrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier)
                    .padding(horizontal = BandStageHorizontalPadding)
                    .width(contentWidth),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EqualizerResponseCurve(
                levelsMb = levels,
                minimumMb = model.minimumBandLevelMb,
                maximumMb = model.maximumBandLevelMb,
                enabled = model.enabled,
                highlightedIndex = activeBand,
                topLabel = topLabel,
                bottomLabel = bottomLabel,
                contentDescription = curveDescription,
                surfaceColor = surfaceColor,
                modifier = Modifier.fillMaxWidth().height(curveHeight),
            )
            Row {
                repeat(model.bands.size) { index ->
                    val band = model.bands[index]
                    FaderColumn(
                        label = formatFrequency(band.centerFrequencyHz),
                        value = band.levelMb,
                        valueRange = model.minimumBandLevelMb..model.maximumBandLevelMb,
                        valueText = formatCompactDecibels(band.levelMb),
                        valueDescription = formatDecibels(band.levelMb),
                        enabled = model.enabled,
                        faderWidth = minOf(BandFaderWidth, columnWidth - 8.dp),
                        faderHeight = faderHeight,
                        prominent = false,
                        resetLabel = resetLabel,
                        onValueChange = { actions.onBandValueChange(band.index, it) },
                        onValueChangeFinished = actions.onBandValueChangeFinished,
                        onActiveChange = { active -> onBandActiveChange(index, active) },
                        modifier = Modifier.width(columnWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun FaderColumn(
    label: String,
    value: Int,
    valueRange: IntRange,
    valueText: String,
    valueDescription: String,
    enabled: Boolean,
    faderWidth: Dp,
    faderHeight: Dp,
    prominent: Boolean,
    resetLabel: String,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    var active by remember { mutableStateOf(false) }
    val emphasis by animateFloatAsState(if (active) 1f else 0f, motion.fastSpatialSpec(), label = "faderValueEmphasis")
    val valueColor by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        motion.defaultEffectsSpec(),
        label = "faderValueColor",
    )
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = valueText,
            style = if (prominent) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.labelLargeEmphasized,
            color = valueColor,
            maxLines = 1,
            modifier =
                Modifier
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        val scale = 1f + 0.18f * emphasis
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
        )
        Spacer(Modifier.height(10.dp))
        EqualizerFader(
            value = value,
            valueRange = valueRange,
            label = label,
            valueDescription = valueDescription,
            enabled = enabled,
            orientation = FaderOrientation.Vertical,
            bipolar = true,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            resetValue = 0,
            resetLabel = resetLabel,
            onActiveChange = {
                active = it
                onActiveChange(it)
            },
            modifier = Modifier.width(faderWidth).height(faderHeight),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = if (prominent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun EffectsSection(
    model: EqualizerUiModel,
    actions: EqualizerActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(title = stringResource(R.string.eq_effects), supporting = stringResource(R.string.eq_effects_description))
        val bassBoost: @Composable (Modifier) -> Unit = { tileModifier ->
            EffectTile(
                iconRes = R.drawable.bolt,
                title = stringResource(R.string.eq_bass_boost),
                description = stringResource(R.string.eq_bass_boost_description),
                note = null,
                checked = model.bassBoostEnabled,
                controlsEnabled = model.enabled,
                value = model.bassBoostStrength,
                valueRange = 0..1000,
                valueText = stringResource(R.string.eq_percent, model.bassBoostStrength / 10),
                bipolar = false,
                onCheckedChange = actions.onBassBoostEnabledChange,
                onValueChange = actions.onBassBoostValueChange,
                onValueChangeFinished = actions.onBassBoostValueChangeFinished,
                modifier = tileModifier,
            )
        }
        val virtualizer: @Composable (Modifier) -> Unit = { tileModifier ->
            EffectTile(
                iconRes = R.drawable.waves,
                title = stringResource(R.string.eq_virtualizer),
                description = stringResource(R.string.eq_virtualizer_description),
                note = null,
                checked = model.virtualizerEnabled,
                controlsEnabled = model.enabled,
                value = model.virtualizerStrength,
                valueRange = 0..1000,
                valueText = stringResource(R.string.eq_percent, model.virtualizerStrength / 10),
                bipolar = false,
                onCheckedChange = actions.onVirtualizerEnabledChange,
                onValueChange = actions.onVirtualizerValueChange,
                onValueChangeFinished = actions.onVirtualizerValueChangeFinished,
                modifier = tileModifier,
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= SideBySideTilesMinWidth) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    bassBoost(Modifier.weight(1f).fillMaxHeight())
                    virtualizer(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    bassBoost(Modifier.fillMaxWidth())
                    virtualizer(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SignalSection(
    model: EqualizerUiModel,
    actions: EqualizerActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(title = stringResource(R.string.eq_signal), supporting = stringResource(R.string.eq_signal_description))
        EffectTile(
            iconRes = R.drawable.volume_up,
            title = stringResource(R.string.eq_output_gain),
            description = stringResource(R.string.eq_output_gain_description),
            note = if (model.autoHeadroomEnabled) stringResource(R.string.eq_output_gain_managed) else null,
            checked = model.outputGainEnabled,
            controlsEnabled = model.enabled && !model.autoHeadroomEnabled,
            value = model.outputGainMb,
            valueRange = -1500..1500,
            valueText = formatDecibels(model.outputGainMb),
            bipolar = true,
            onCheckedChange = actions.onOutputGainEnabledChange,
            onValueChange = actions.onOutputGainValueChange,
            onValueChangeFinished = actions.onOutputGainValueChangeFinished,
            modifier = Modifier.fillMaxWidth(),
        )
        ToggleTile(
            iconRes = R.drawable.tune,
            title = stringResource(R.string.eq_auto_headroom),
            description = stringResource(R.string.eq_auto_headroom_description),
            checked = model.autoHeadroomEnabled,
            enabled = model.enabled,
            onCheckedChange = actions.onAutoHeadroomEnabledChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EffectTile(
    iconRes: Int,
    title: String,
    description: String,
    note: String?,
    checked: Boolean,
    controlsEnabled: Boolean,
    value: Int,
    valueRange: IntRange,
    valueText: String,
    bipolar: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val faderEnabled = controlsEnabled && checked
    val valueColor by animateColorAsState(
        if (faderEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        motion.defaultEffectsSpec(),
        label = "effectValueColor",
    )
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = checked, enabled = controlsEnabled, role = Role.Switch, onValueChange = onCheckedChange)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                EffectBadge(iconRes = iconRes, active = faderEnabled)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedVisibility(
                        visible = note != null,
                        enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.defaultSpatialSpec()),
                    ) {
                        Text(
                            text = note.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Switch(checked = checked, onCheckedChange = null, enabled = controlsEnabled)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EqualizerFader(
                    value = value,
                    valueRange = valueRange,
                    label = title,
                    valueDescription = valueText,
                    enabled = faderEnabled,
                    orientation = FaderOrientation.Horizontal,
                    bipolar = bipolar,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueChangeFinished,
                    keyStep = 50,
                    resetValue = if (bipolar) 0 else null,
                    resetLabel = if (bipolar) stringResource(R.string.eq_reset_to_zero) else null,
                    modifier = Modifier.weight(1f).height(HorizontalFaderHeight),
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = valueColor,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 64.dp).clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun ToggleTile(
    iconRes: Int,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EffectBadge(iconRes = iconRes, active = enabled && checked)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun EffectBadge(
    iconRes: Int,
    active: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        motion.defaultEffectsSpec(),
        label = "effectBadgeContainer",
    )
    val content by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        motion.defaultEffectsSpec(),
        label = "effectBadgeContent",
    )
    Box(
        modifier = Modifier.size(44.dp).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ProfilesSection(
    model: EqualizerUiModel,
    actions: EqualizerActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(
            title = stringResource(R.string.eq_profiles),
            supporting = stringResource(R.string.eq_profiles_description),
            action = {
                TextButton(
                    onClick = actions.onShowManageProfiles,
                    enabled = model.profiles.size > 0,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(R.string.eq_manage))
                }
            },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "save", contentType = "action") {
                ProfileActionCard(
                    iconRes = R.drawable.add,
                    label = stringResource(R.string.eq_save_profile),
                    emphasized = true,
                    enabled = model.enabled,
                    onClick = actions.onShowSaveProfile,
                )
            }
            items(count = model.profiles.size, key = { model.profiles[it].id }, contentType = { "profile" }) { index ->
                ProfileCard(profile = model.profiles[index], onApply = actions.onApplyProfile)
            }
            item(key = "import", contentType = "action") {
                ProfileActionCard(
                    iconRes = R.drawable.download,
                    label = stringResource(R.string.eq_import),
                    emphasized = false,
                    enabled = true,
                    onClick = actions.onImportProfiles,
                )
            }
        }
        if (model.profiles.size == 0) {
            Text(
                text = stringResource(R.string.eq_no_profiles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun ProfileCard(
    profile: EqualizerProfileUiModel,
    onApply: (String) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (profile.isSelected) colors.secondaryContainer else colors.surfaceContainerLow,
        motion.defaultEffectsSpec(),
        label = "profileContainer",
    )
    val content by animateColorAsState(
        if (profile.isSelected) colors.onSecondaryContainer else colors.onSurface,
        motion.defaultEffectsSpec(),
        label = "profileContent",
    )
    Surface(
        selected = profile.isSelected,
        onClick = { onApply(profile.id) },
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = Modifier.width(ProfileCardWidth).heightIn(min = ProfileCardMinHeight),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(if (profile.isSelected) MaterialShapes.Cookie6Sided.toShape() else CircleShape)
                        .background(if (profile.isSelected) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (profile.isSelected) R.drawable.check else R.drawable.equalizer),
                    contentDescription = null,
                    tint = if (profile.isSelected) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = profile.name.ifBlank { stringResource(R.string.eq_imported_profile) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.eq_custom_profile),
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ProfileActionCard(
    iconRes: Int,
    label: String,
    emphasized: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (emphasized) colors.tertiaryContainer else colors.surfaceContainer,
        contentColor = if (emphasized) colors.onTertiaryContainer else colors.onSurface,
        modifier =
            Modifier
                .width(ProfileCardWidth)
                .heightIn(min = ProfileCardMinHeight)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).background(colors.surface.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(text = label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
private fun SaveProfileDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painter = painterResource(R.drawable.equalizer), contentDescription = null) },
        title = { Text(text = stringResource(R.string.eq_save_profile)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(text = stringResource(R.string.eq_profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = name.isNotBlank(), shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(text = stringResource(R.string.eq_close)) }
        },
    )
}

@Composable
private fun ManageProfilesDialog(
    profiles: EqualizerProfileUiModels,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.eq_profiles)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                items(count = profiles.size, key = { profiles[it].id }, contentType = { "profile" }) { index ->
                    ProfileRow(profiles[index], index, profiles.size, onApply, onDelete, onExport)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(text = stringResource(R.string.eq_close)) }
        },
    )
}

@Composable
private fun ProfileRow(
    profile: EqualizerProfileUiModel,
    index: Int,
    count: Int,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
) {
    SegmentedListItem(
        onClick = { onApply(profile.id) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = {
            Icon(
                painter = painterResource(if (profile.isSelected) R.drawable.check else R.drawable.equalizer),
                contentDescription = null,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = { onExport(profile.id) }) {
                    Icon(painter = painterResource(R.drawable.share), contentDescription = stringResource(R.string.export))
                }
                IconButton(onClick = { onDelete(profile.id) }) {
                    Icon(painter = painterResource(R.drawable.delete), contentDescription = stringResource(R.string.delete))
                }
            }
        },
        supportingContent = { Text(text = stringResource(R.string.eq_custom_profile)) },
        content = {
            Text(
                text = profile.name.ifBlank { stringResource(R.string.eq_imported_profile) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun EqualizerLoading(contentPadding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun EqualizerMessage(
    contentPadding: PaddingValues,
    message: String,
    onOpenSystemEqualizer: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(112.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.graphic_eq),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text(text = message, style = MaterialTheme.typography.titleMediumEmphasized, textAlign = TextAlign.Center)
            FilledTonalButton(onClick = onOpenSystemEqualizer, shapes = ButtonDefaults.shapes()) {
                Icon(painter = painterResource(R.drawable.tune), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.eq_open_system_equalizer))
            }
        }
    }
}

@Composable
private fun activeSoundLabel(model: EqualizerUiModel): String {
    for (index in 0 until model.presets.size) {
        val preset = model.presets[index]
        if (preset.isSelected) return presetLabel(preset, index)
    }
    for (index in 0 until model.profiles.size) {
        val profile = model.profiles[index]
        if (profile.isSelected) return profile.name.ifBlank { stringResource(R.string.eq_imported_profile) }
    }
    return stringResource(R.string.eq_custom_sound)
}

@Composable
private fun presetLabel(
    preset: EqualizerPresetUiModel,
    index: Int,
): String =
    when {
        preset.id == FLAT_PRESET_ID -> stringResource(R.string.eq_flat)
        preset.name.isNullOrBlank() -> stringResource(R.string.eq_preset_number, index)
        else -> preset.name.orEmpty()
    }

@Composable
private fun formatDecibels(valueMb: Int): String = stringResource(R.string.eq_decibels, valueMb / 100f)

@Composable
private fun formatCompactDecibels(valueMb: Int): String = stringResource(R.string.eq_decibels_compact, valueMb / 100f)

@Composable
private fun formatFrequency(frequencyHz: Int): String =
    if (frequencyHz >= 1000) {
        stringResource(R.string.eq_frequency_kilohertz, frequencyHz / 1000f)
    } else {
        stringResource(R.string.eq_frequency_hertz, frequencyHz)
    }

private const val FLAT_PRESET_ID = "flat"
private val ExpandedLayoutMinWidth = 840.dp
private val CompactContentMaxWidth = 720.dp
private val SideBySideTilesMinWidth = 560.dp
private val ModeButtonHeight = 52.dp
private val ToneFaderWidth = 64.dp
private val BandFaderWidth = 48.dp
private val BandColumnMinWidth = 56.dp
private val BandStageHorizontalPadding = 12.dp
private val HorizontalFaderHeight = 44.dp
private val ProfileCardWidth = 148.dp
private val ProfileCardMinHeight = 128.dp
