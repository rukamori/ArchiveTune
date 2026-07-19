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
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.equalizer.EqualizerControlMode
import moe.rukamori.archivetune.equalizer.EqualizerTone
import moe.rukamori.archivetune.viewmodels.EqualizerBandUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerEffect
import moe.rukamori.archivetune.viewmodels.EqualizerProfileUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerScreenState
import moe.rukamori.archivetune.viewmodels.EqualizerToneUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerUiModel
import moe.rukamori.archivetune.viewmodels.EqualizerViewModel
import kotlin.math.roundToInt

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        EqualizerScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onDismiss = onDismiss,
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
}

@Composable
private fun EqualizerScreen(
    state: EqualizerScreenState,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onOpenSystemEqualizer: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (EqualizerControlMode) -> Unit,
    onPresetClick: (String) -> Unit,
    onToneValueChange: (EqualizerTone, Int) -> Unit,
    onToneValueChangeFinished: (EqualizerTone) -> Unit,
    onBandValueChange: (Int, Int) -> Unit,
    onBandValueChangeFinished: () -> Unit,
    onResetBands: () -> Unit,
    onOutputGainEnabledChange: (Boolean) -> Unit,
    onOutputGainValueChange: (Int) -> Unit,
    onOutputGainValueChangeFinished: () -> Unit,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostValueChange: (Int) -> Unit,
    onBassBoostValueChangeFinished: () -> Unit,
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerValueChange: (Int) -> Unit,
    onVirtualizerValueChangeFinished: () -> Unit,
    onAutoHeadroomEnabledChange: (Boolean) -> Unit,
    onShowSaveProfile: () -> Unit,
    onProfileNameChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onDismissSaveProfile: () -> Unit,
    onShowManageProfiles: () -> Unit,
    onDismissManageProfiles: () -> Unit,
    onApplyProfile: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onImportProfiles: () -> Unit,
    onExportProfile: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = stringResource(R.string.equalizer)) },
                subtitle = { Text(text = stringResource(R.string.eq_screen_subtitle)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(painter = painterResource(R.drawable.close), contentDescription = null)
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
        when (state) {
            EqualizerScreenState.Loading -> {
                EqualizerLoading(contentPadding)
            }

            EqualizerScreenState.Empty -> {
                EqualizerUnavailable(contentPadding, onOpenSystemEqualizer)
            }

            is EqualizerScreenState.Error -> {
                EqualizerError(contentPadding, state.messageResId, onOpenSystemEqualizer)
            }

            is EqualizerScreenState.Success -> {
                EqualizerContent(
                    model = state.model,
                    contentPadding = contentPadding,
                    onOpenSystemEqualizer = onOpenSystemEqualizer,
                    onEnabledChange = onEnabledChange,
                    onModeChange = onModeChange,
                    onPresetClick = onPresetClick,
                    onToneValueChange = onToneValueChange,
                    onToneValueChangeFinished = onToneValueChangeFinished,
                    onBandValueChange = onBandValueChange,
                    onBandValueChangeFinished = onBandValueChangeFinished,
                    onResetBands = onResetBands,
                    onOutputGainEnabledChange = onOutputGainEnabledChange,
                    onOutputGainValueChange = onOutputGainValueChange,
                    onOutputGainValueChangeFinished = onOutputGainValueChangeFinished,
                    onBassBoostEnabledChange = onBassBoostEnabledChange,
                    onBassBoostValueChange = onBassBoostValueChange,
                    onBassBoostValueChangeFinished = onBassBoostValueChangeFinished,
                    onVirtualizerEnabledChange = onVirtualizerEnabledChange,
                    onVirtualizerValueChange = onVirtualizerValueChange,
                    onVirtualizerValueChangeFinished = onVirtualizerValueChangeFinished,
                    onAutoHeadroomEnabledChange = onAutoHeadroomEnabledChange,
                    onShowSaveProfile = onShowSaveProfile,
                    onShowManageProfiles = onShowManageProfiles,
                    onImportProfiles = onImportProfiles,
                )
            }
        }
    }

    val model = (state as? EqualizerScreenState.Success)?.model
    if (model?.saveProfileDialog?.visible == true) {
        SaveProfileDialog(
            name = model.saveProfileDialog.name,
            onNameChange = onProfileNameChange,
            onSave = onSaveProfile,
            onDismiss = onDismissSaveProfile,
        )
    }
    if (model?.manageProfilesVisible == true) {
        ManageProfilesDialog(
            profiles = model.profiles,
            onApply = onApplyProfile,
            onDelete = onDeleteProfile,
            onExport = onExportProfile,
            onDismiss = onDismissManageProfiles,
        )
    }
}

@Composable
private fun EqualizerContent(
    model: EqualizerUiModel,
    contentPadding: PaddingValues,
    onOpenSystemEqualizer: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (EqualizerControlMode) -> Unit,
    onPresetClick: (String) -> Unit,
    onToneValueChange: (EqualizerTone, Int) -> Unit,
    onToneValueChangeFinished: (EqualizerTone) -> Unit,
    onBandValueChange: (Int, Int) -> Unit,
    onBandValueChangeFinished: () -> Unit,
    onResetBands: () -> Unit,
    onOutputGainEnabledChange: (Boolean) -> Unit,
    onOutputGainValueChange: (Int) -> Unit,
    onOutputGainValueChangeFinished: () -> Unit,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostValueChange: (Int) -> Unit,
    onBassBoostValueChangeFinished: () -> Unit,
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerValueChange: (Int) -> Unit,
    onVirtualizerValueChangeFinished: () -> Unit,
    onAutoHeadroomEnabledChange: (Boolean) -> Unit,
    onShowSaveProfile: () -> Unit,
    onShowManageProfiles: () -> Unit,
    onImportProfiles: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 28.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero", contentType = "hero") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                EqualizerHero(model.enabled, Modifier.widthIn(max = 840.dp), onEnabledChange, onOpenSystemEqualizer)
            }
        }
        item(key = "mode", contentType = "mode") {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ModeSelector(model.controlMode, Modifier.widthIn(max = 840.dp), onModeChange)
            }
        }
        item(key = "controls", contentType = model.controlMode.storageValue) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                AnimatedContent(
                    targetState = model.controlMode,
                    transitionSpec = {
                        (fadeIn(spring()) togetherWith fadeOut(spring())).using(SizeTransform(clip = false))
                    },
                    label = "equalizerMode",
                    modifier = Modifier.widthIn(max = 840.dp),
                ) { mode ->
                    when (mode) {
                        EqualizerControlMode.BASIC -> {
                            BasicControls(
                                model = model,
                                onPresetClick = onPresetClick,
                                onToneValueChange = onToneValueChange,
                                onToneValueChangeFinished = onToneValueChangeFinished,
                            )
                        }

                        EqualizerControlMode.ADVANCED -> {
                            AdvancedControls(
                                model = model,
                                onPresetClick = onPresetClick,
                                onBandValueChange = onBandValueChange,
                                onBandValueChangeFinished = onBandValueChangeFinished,
                                onResetBands = onResetBands,
                                onOutputGainEnabledChange = onOutputGainEnabledChange,
                                onOutputGainValueChange = onOutputGainValueChange,
                                onOutputGainValueChangeFinished = onOutputGainValueChangeFinished,
                                onBassBoostEnabledChange = onBassBoostEnabledChange,
                                onBassBoostValueChange = onBassBoostValueChange,
                                onBassBoostValueChangeFinished = onBassBoostValueChangeFinished,
                                onVirtualizerEnabledChange = onVirtualizerEnabledChange,
                                onVirtualizerValueChange = onVirtualizerValueChange,
                                onVirtualizerValueChangeFinished = onVirtualizerValueChangeFinished,
                                onAutoHeadroomEnabledChange = onAutoHeadroomEnabledChange,
                                onShowSaveProfile = onShowSaveProfile,
                                onShowManageProfiles = onShowManageProfiles,
                                onImportProfiles = onImportProfiles,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerHero(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onEnabledChange: (Boolean) -> Unit,
    onOpenSystemEqualizer: () -> Unit,
) {
    val containerColor =
        if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (enabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)) {
                    Icon(
                        painter = painterResource(R.drawable.graphic_eq),
                        contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(28.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(if (enabled) R.string.eq_sound_shaping_on else R.string.eq_sound_shaping_off),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.eq_enable_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
            FilledTonalButton(onClick = onOpenSystemEqualizer, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Icon(painter = painterResource(R.drawable.tune), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.eq_open_system_equalizer))
            }
        }
    }
}

@Composable
private fun ModeSelector(
    selectedMode: EqualizerControlMode,
    modifier: Modifier = Modifier,
    onModeChange: (EqualizerControlMode) -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = stringResource(R.string.eq_control_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EqualizerControlMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, EqualizerControlMode.entries.size),
                        icon = {},
                    ) {
                        Text(text = stringResource(if (mode == EqualizerControlMode.BASIC) R.string.eq_basic else R.string.eq_advanced))
                    }
                }
            }
            Text(
                text =
                    stringResource(
                        if (selectedMode ==
                            EqualizerControlMode.BASIC
                        ) {
                            R.string.eq_basic_description
                        } else {
                            R.string.eq_advanced_description
                        },
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BasicControls(
    model: EqualizerUiModel,
    onPresetClick: (String) -> Unit,
    onToneValueChange: (EqualizerTone, Int) -> Unit,
    onToneValueChangeFinished: (EqualizerTone) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PresetSection(model, onPresetClick)
        EqualizerSection(title = stringResource(R.string.eq_tone), subtitle = stringResource(R.string.eq_tone_description)) {
            repeat(model.tones.size) { index ->
                val tone = model.tones[index]
                ToneSlider(
                    model = tone,
                    enabled = model.enabled,
                    minimumValueMb = model.minimumBandLevelMb,
                    maximumValueMb = model.maximumBandLevelMb,
                    onValueChange = onToneValueChange,
                    onValueChangeFinished = onToneValueChangeFinished,
                )
                if (index != model.tones.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun AdvancedControls(
    model: EqualizerUiModel,
    onPresetClick: (String) -> Unit,
    onBandValueChange: (Int, Int) -> Unit,
    onBandValueChangeFinished: () -> Unit,
    onResetBands: () -> Unit,
    onOutputGainEnabledChange: (Boolean) -> Unit,
    onOutputGainValueChange: (Int) -> Unit,
    onOutputGainValueChangeFinished: () -> Unit,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostValueChange: (Int) -> Unit,
    onBassBoostValueChangeFinished: () -> Unit,
    onVirtualizerEnabledChange: (Boolean) -> Unit,
    onVirtualizerValueChange: (Int) -> Unit,
    onVirtualizerValueChangeFinished: () -> Unit,
    onAutoHeadroomEnabledChange: (Boolean) -> Unit,
    onShowSaveProfile: () -> Unit,
    onShowManageProfiles: () -> Unit,
    onImportProfiles: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PresetSection(model, onPresetClick)
        EqualizerSection(
            title = stringResource(R.string.eq_bands),
            subtitle = stringResource(R.string.eq_bands_description),
            action = {
                TextButton(onClick = onResetBands, enabled = model.enabled, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.reset))
                }
            },
        ) {
            repeat(model.bands.size) { index ->
                BandSlider(
                    model = model.bands[index],
                    enabled = model.enabled,
                    minimumValueMb = model.minimumBandLevelMb,
                    maximumValueMb = model.maximumBandLevelMb,
                    onValueChange = onBandValueChange,
                    onValueChangeFinished = onBandValueChangeFinished,
                )
                if (index != model.bands.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        EqualizerSection(title = stringResource(R.string.eq_signal), subtitle = stringResource(R.string.eq_signal_description)) {
            ToggleSlider(
                title = stringResource(R.string.eq_output_gain),
                description = stringResource(R.string.eq_output_gain_description),
                enabled = model.outputGainEnabled,
                controlsEnabled = model.enabled && !model.autoHeadroomEnabled,
                value = model.outputGainMb,
                valueRange = -1500..1500,
                valueLabel = formatDecibels(model.outputGainMb),
                onEnabledChange = onOutputGainEnabledChange,
                onValueChange = onOutputGainValueChange,
                onValueChangeFinished = onOutputGainValueChangeFinished,
            )
            Spacer(Modifier.height(12.dp))
            SettingsToggle(
                title = stringResource(R.string.eq_auto_headroom),
                description = stringResource(R.string.eq_auto_headroom_description),
                checked = model.autoHeadroomEnabled,
                enabled = model.enabled,
                onCheckedChange = onAutoHeadroomEnabledChange,
            )
        }
        EqualizerSection(title = stringResource(R.string.eq_effects), subtitle = stringResource(R.string.eq_effects_description)) {
            ToggleSlider(
                title = stringResource(R.string.eq_bass_boost),
                description = stringResource(R.string.eq_bass_boost_description),
                enabled = model.bassBoostEnabled,
                controlsEnabled = model.enabled,
                value = model.bassBoostStrength,
                valueRange = 0..1000,
                valueLabel = stringResource(R.string.eq_percent, model.bassBoostStrength / 10),
                onEnabledChange = onBassBoostEnabledChange,
                onValueChange = onBassBoostValueChange,
                onValueChangeFinished = onBassBoostValueChangeFinished,
            )
            Spacer(Modifier.height(12.dp))
            ToggleSlider(
                title = stringResource(R.string.eq_virtualizer),
                description = stringResource(R.string.eq_virtualizer_description),
                enabled = model.virtualizerEnabled,
                controlsEnabled = model.enabled,
                value = model.virtualizerStrength,
                valueRange = 0..1000,
                valueLabel = stringResource(R.string.eq_percent, model.virtualizerStrength / 10),
                onEnabledChange = onVirtualizerEnabledChange,
                onValueChange = onVirtualizerValueChange,
                onValueChangeFinished = onVirtualizerValueChangeFinished,
            )
        }
        EqualizerSection(title = stringResource(R.string.eq_profiles), subtitle = stringResource(R.string.eq_profiles_description)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onShowSaveProfile, enabled = model.enabled, shapes = ButtonDefaults.shapes()) {
                    Icon(painter = painterResource(R.drawable.add), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.eq_save_profile))
                }
                OutlinedButton(onClick = onShowManageProfiles, enabled = model.profiles.size > 0, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.eq_manage))
                }
                OutlinedButton(onClick = onImportProfiles, shapes = ButtonDefaults.shapes()) {
                    Text(text = stringResource(R.string.eq_import))
                }
            }
        }
    }
}

@Composable
private fun PresetSection(
    model: EqualizerUiModel,
    onPresetClick: (String) -> Unit,
) {
    EqualizerSection(title = stringResource(R.string.eq_presets), subtitle = stringResource(R.string.eq_presets_description)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(count = model.presets.size, key = { model.presets[it].id }, contentType = { "preset" }) { index ->
                val preset = model.presets[index]
                FilterChip(
                    selected = preset.isSelected,
                    onClick = { onPresetClick(preset.id) },
                    enabled = model.enabled,
                    label = {
                        Text(
                            text =
                                when {
                                    preset.id == "flat" -> stringResource(R.string.eq_flat)
                                    preset.name.isNullOrBlank() -> stringResource(R.string.eq_preset_number, index)
                                    else -> preset.name.orEmpty()
                                },
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer),
                )
            }
        }
    }
}

@Composable
private fun EqualizerSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                action?.invoke()
            }
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
private fun ToneSlider(
    model: EqualizerToneUiModel,
    enabled: Boolean,
    minimumValueMb: Int,
    maximumValueMb: Int,
    onValueChange: (EqualizerTone, Int) -> Unit,
    onValueChangeFinished: (EqualizerTone) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    stringResource(
                        when (model.tone) {
                            EqualizerTone.BASS -> R.string.eq_bass
                            EqualizerTone.MIDRANGE -> R.string.eq_midrange
                            EqualizerTone.TREBLE -> R.string.eq_treble
                        },
                    ),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            ValuePill(formatDecibels(model.levelMb))
        }
        Slider(
            value = model.levelMb.toFloat(),
            onValueChange = { onValueChange(model.tone, it.roundToInt()) },
            onValueChangeFinished = { onValueChangeFinished(model.tone) },
            enabled = enabled,
            valueRange = minimumValueMb.toFloat()..maximumValueMb.toFloat(),
        )
    }
}

@Composable
private fun BandSlider(
    model: EqualizerBandUiModel,
    enabled: Boolean,
    minimumValueMb: Int,
    maximumValueMb: Int,
    onValueChange: (Int, Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatFrequency(model.centerFrequencyHz),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            ValuePill(formatDecibels(model.levelMb))
        }
        Slider(
            value = model.levelMb.toFloat(),
            onValueChange = { onValueChange(model.index, it.roundToInt()) },
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = minimumValueMb.toFloat()..maximumValueMb.toFloat(),
        )
    }
}

@Composable
private fun ToggleSlider(
    title: String,
    description: String,
    enabled: Boolean,
    controlsEnabled: Boolean,
    value: Int,
    valueRange: IntRange,
    valueLabel: String,
    onEnabledChange: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange, enabled = controlsEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onValueChange(it.roundToInt()) },
                    onValueChangeFinished = onValueChangeFinished,
                    enabled = controlsEnabled && enabled,
                    valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                ValuePill(valueLabel)
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = { Text(text = description) },
        content = { Text(text = title) },
    )
}

@Composable
private fun ValuePill(value: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
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
            TextButton(onClick = onSave, enabled = name.isNotBlank()) { Text(text = stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.eq_close)) } },
    )
}

@Composable
private fun ManageProfilesDialog(
    profiles: moe.rukamori.archivetune.viewmodels.EqualizerProfileUiModels,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.eq_profiles)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(count = profiles.size, key = { profiles[it].id }, contentType = { "profile" }) { index ->
                    ProfileRow(profiles[index], onApply, onDelete, onExport)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.eq_close)) } },
    )
}

@Composable
private fun ProfileRow(
    profile: EqualizerProfileUiModel,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (String) -> Unit,
) {
    SegmentedListItem(
        onClick = { onApply(profile.id) },
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large),
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
        LoadingIndicator(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun EqualizerUnavailable(
    contentPadding: PaddingValues,
    onOpenSystemEqualizer: () -> Unit,
) {
    EqualizerMessage(
        contentPadding = contentPadding,
        message = stringResource(R.string.eq_waiting_for_audio_session),
        onOpenSystemEqualizer = onOpenSystemEqualizer,
    )
}

@Composable
private fun EqualizerError(
    contentPadding: PaddingValues,
    messageResId: Int,
    onOpenSystemEqualizer: () -> Unit,
) {
    EqualizerMessage(contentPadding, stringResource(messageResId), onOpenSystemEqualizer)
}

@Composable
private fun EqualizerMessage(
    contentPadding: PaddingValues,
    message: String,
    onOpenSystemEqualizer: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painter = painterResource(R.drawable.graphic_eq), contentDescription = null, modifier = Modifier.size(48.dp))
            Text(text = message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            FilledTonalButton(onClick = onOpenSystemEqualizer, shapes = ButtonDefaults.shapes()) {
                Text(text = stringResource(R.string.eq_open_system_equalizer))
            }
        }
    }
}

@Composable
private fun formatDecibels(valueMb: Int): String = stringResource(R.string.eq_decibels, valueMb / 100f)

@Composable
private fun formatFrequency(frequencyHz: Int): String =
    if (frequencyHz >= 1000) {
        stringResource(R.string.eq_frequency_kilohertz, frequencyHz / 1000f)
    } else {
        stringResource(R.string.eq_frequency_hertz, frequencyHz)
    }
