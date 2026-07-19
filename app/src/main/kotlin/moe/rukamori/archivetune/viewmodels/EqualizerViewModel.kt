/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.equalizer.ApplyEqualizerPresetUseCase
import moe.rukamori.archivetune.equalizer.EqualizerConfiguration
import moe.rukamori.archivetune.equalizer.EqualizerControlMode
import moe.rukamori.archivetune.equalizer.EqualizerTone
import moe.rukamori.archivetune.equalizer.ManageEqualizerProfilesUseCase
import moe.rukamori.archivetune.equalizer.ObserveEqualizerUseCase
import moe.rukamori.archivetune.equalizer.UpdateEqualizerUseCase
import moe.rukamori.archivetune.equalizer.equalizerToneIndices
import moe.rukamori.archivetune.equalizer.resampleLevels
import moe.rukamori.archivetune.playback.EqProfile
import javax.inject.Inject

sealed interface EqualizerScreenState {
    data object Loading : EqualizerScreenState

    data class Success(
        val model: EqualizerUiModel,
    ) : EqualizerScreenState

    data object Empty : EqualizerScreenState

    data class Error(
        val messageResId: Int,
    ) : EqualizerScreenState
}

@Immutable
data class EqualizerUiModel(
    val enabled: Boolean,
    val controlMode: EqualizerControlMode,
    val selectedProfileId: String,
    val presets: EqualizerPresetUiModels,
    val tones: EqualizerToneUiModels,
    val bands: EqualizerBandUiModels,
    val minimumBandLevelMb: Int,
    val maximumBandLevelMb: Int,
    val outputGainEnabled: Boolean,
    val outputGainMb: Int,
    val bassBoostEnabled: Boolean,
    val bassBoostStrength: Int,
    val virtualizerEnabled: Boolean,
    val virtualizerStrength: Int,
    val autoHeadroomEnabled: Boolean,
    val profiles: EqualizerProfileUiModels,
    val saveProfileDialog: SaveEqualizerProfileUiModel,
    val manageProfilesVisible: Boolean,
)

@Immutable
data class EqualizerPresetUiModels(
    private val values: List<EqualizerPresetUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): EqualizerPresetUiModel = values[index]
}

@Immutable
data class EqualizerPresetUiModel(
    val id: String,
    val name: String?,
    val isSelected: Boolean,
)

@Immutable
data class EqualizerToneUiModels(
    private val values: List<EqualizerToneUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): EqualizerToneUiModel = values[index]
}

@Immutable
data class EqualizerToneUiModel(
    val tone: EqualizerTone,
    val levelMb: Int,
)

@Immutable
data class EqualizerBandUiModels(
    private val values: List<EqualizerBandUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): EqualizerBandUiModel = values[index]
}

@Immutable
data class EqualizerBandUiModel(
    val index: Int,
    val centerFrequencyHz: Int,
    val levelMb: Int,
)

@Immutable
data class EqualizerProfileUiModels(
    private val values: List<EqualizerProfileUiModel>,
) {
    val size: Int get() = values.size

    operator fun get(index: Int): EqualizerProfileUiModel = values[index]
}

@Immutable
data class EqualizerProfileUiModel(
    val id: String,
    val name: String,
    val isSelected: Boolean,
)

@Immutable
data class SaveEqualizerProfileUiModel(
    val visible: Boolean = false,
    val name: String = "",
)

sealed interface EqualizerEffect {
    data class ShowMessage(
        val messageResId: Int,
        val quantity: Int? = null,
    ) : EqualizerEffect

    data object OpenImportDocument : EqualizerEffect

    data class CreateExportDocument(
        val suggestedName: String,
    ) : EqualizerEffect
}

private data class EqualizerDraft(
    val bandLevelsMb: List<Int>? = null,
    val toneLevelsMb: Map<EqualizerTone, Int> = emptyMap(),
    val outputGainMb: Int? = null,
    val bassBoostStrength: Int? = null,
    val virtualizerStrength: Int? = null,
)

private sealed interface EqualizerConfigurationResult {
    data object Loading : EqualizerConfigurationResult

    data class Data(
        val configuration: EqualizerConfiguration,
    ) : EqualizerConfigurationResult

    data object Error : EqualizerConfigurationResult
}

@HiltViewModel
class EqualizerViewModel
    @Inject
    constructor(
        observeEqualizer: ObserveEqualizerUseCase,
        private val updateEqualizer: UpdateEqualizerUseCase,
        private val applyPreset: ApplyEqualizerPresetUseCase,
        private val manageProfiles: ManageEqualizerProfilesUseCase,
    ) : ViewModel() {
        private val effectsFlow = MutableSharedFlow<EqualizerEffect>(extraBufferCapacity = 2)
        val effects = effectsFlow.asSharedFlow()

        private val draft = MutableStateFlow(EqualizerDraft())
        private val saveDialog = MutableStateFlow(SaveEqualizerProfileUiModel())
        private val manageProfilesVisible = MutableStateFlow(false)
        private var pendingExportProfileId: String? = null

        private val configurationResult: StateFlow<EqualizerConfigurationResult> =
            observeEqualizer()
                .map<EqualizerConfiguration, EqualizerConfigurationResult> { configuration ->
                    EqualizerConfigurationResult.Data(configuration)
                }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    emit(EqualizerConfigurationResult.Error)
                }.stateIn(viewModelScope, SharingStarted.Eagerly, EqualizerConfigurationResult.Loading)

        private val configuration: EqualizerConfiguration?
            get() = (configurationResult.value as? EqualizerConfigurationResult.Data)?.configuration

        private var bandCommitJob: Job? = null
        private var outputGainCommitJob: Job? = null
        private var bassBoostCommitJob: Job? = null
        private var virtualizerCommitJob: Job? = null

        val state: StateFlow<EqualizerScreenState> =
            combine(configurationResult, draft, saveDialog, manageProfilesVisible) { result, currentDraft, profileDialog, profilesVisible ->
                when (result) {
                    EqualizerConfigurationResult.Loading -> {
                        EqualizerScreenState.Loading
                    }

                    EqualizerConfigurationResult.Error -> {
                        EqualizerScreenState.Error(R.string.error_unknown)
                    }

                    is EqualizerConfigurationResult.Data -> {
                        val config = result.configuration
                        if (config.capabilities == null || config.capabilities.bandCount <= 0) {
                            EqualizerScreenState.Empty
                        } else {
                            EqualizerScreenState.Success(
                                config.toUiModel(
                                    draft = currentDraft,
                                    saveDialog = profileDialog,
                                    manageProfilesVisible = profilesVisible,
                                ),
                            )
                        }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqualizerScreenState.Loading)

        fun setEnabled(enabled: Boolean) = launchUpdate { updateEqualizer.setEnabled(enabled) }

        fun setControlMode(mode: EqualizerControlMode) = launchUpdate { updateEqualizer.setControlMode(mode) }

        fun applyPreset(presetId: String) {
            draft.value = EqualizerDraft()
            if (!applyPreset.invoke(presetId)) {
                effectsFlow.tryEmit(EqualizerEffect.ShowMessage(R.string.eq_waiting_for_audio_session))
            }
        }

        fun updateToneDraft(
            tone: EqualizerTone,
            valueMb: Int,
        ) {
            val config = configuration ?: return
            draft.update { current ->
                val configWithDraft = config.withDraft(current)
                current.copy(
                    bandLevelsMb = updateEqualizer.adjustToneBands(tone, valueMb, configWithDraft),
                    toneLevelsMb = current.toneLevelsMb + (tone to valueMb),
                )
            }
        }

        fun commitTone(tone: EqualizerTone) {
            val levels = draft.value.bandLevelsMb ?: return
            draft.update { it.copy(toneLevelsMb = it.toneLevelsMb - tone) }
            bandCommitJob?.cancel()
            bandCommitJob = launchUpdate { updateEqualizer.updateBandLevels(levels) }
        }

        fun updateBandDraft(
            index: Int,
            valueMb: Int,
        ) {
            val config = configuration ?: return
            val capabilities = config.capabilities ?: return
            draft.update { current ->
                val levels =
                    resampleLevels(
                        levelsMb = current.bandLevelsMb ?: config.settings.bandLevelsMb,
                        targetCount = capabilities.bandCount,
                    ).toMutableList()
                if (index in levels.indices) levels[index] = valueMb
                current.copy(bandLevelsMb = levels)
            }
        }

        fun commitBands() {
            val levels = draft.value.bandLevelsMb ?: return
            bandCommitJob?.cancel()
            bandCommitJob = launchUpdate { updateEqualizer.updateBandLevels(levels) }
        }

        fun resetBands() {
            val config = configuration ?: return
            draft.value = EqualizerDraft()
            launchUpdate { updateEqualizer.resetBands(config) }
        }

        fun setOutputGainEnabled(enabled: Boolean) = launchUpdate { updateEqualizer.setOutputGainEnabled(enabled) }

        fun updateOutputGainDraft(valueMb: Int) = draft.update { it.copy(outputGainMb = valueMb) }

        fun commitOutputGain() {
            val value = draft.value.outputGainMb ?: return
            outputGainCommitJob?.cancel()
            outputGainCommitJob = launchUpdate { updateEqualizer.setOutputGainMb(value) }
        }

        fun setBassBoostEnabled(enabled: Boolean) = launchUpdate { updateEqualizer.setBassBoostEnabled(enabled) }

        fun updateBassBoostDraft(value: Int) = draft.update { it.copy(bassBoostStrength = value) }

        fun commitBassBoost() {
            val value = draft.value.bassBoostStrength ?: return
            bassBoostCommitJob?.cancel()
            bassBoostCommitJob = launchUpdate { updateEqualizer.setBassBoostStrength(value) }
        }

        fun setVirtualizerEnabled(enabled: Boolean) = launchUpdate { updateEqualizer.setVirtualizerEnabled(enabled) }

        fun updateVirtualizerDraft(value: Int) = draft.update { it.copy(virtualizerStrength = value) }

        fun commitVirtualizer() {
            val value = draft.value.virtualizerStrength ?: return
            virtualizerCommitJob?.cancel()
            virtualizerCommitJob = launchUpdate { updateEqualizer.setVirtualizerStrength(value) }
        }

        fun setAutoHeadroomEnabled(enabled: Boolean) = launchUpdate { updateEqualizer.setAutoHeadroomEnabled(enabled) }

        fun showSaveProfileDialog() {
            saveDialog.value = SaveEqualizerProfileUiModel(visible = true)
        }

        fun updateProfileName(name: String) {
            saveDialog.update { it.copy(name = name) }
        }

        fun dismissSaveProfileDialog() {
            saveDialog.value = SaveEqualizerProfileUiModel()
        }

        fun saveProfile() {
            val name = saveDialog.value.name.trim()
            val config = configuration ?: return
            if (name.isBlank()) return
            dismissSaveProfileDialog()
            launchUpdate {
                manageProfiles.save(name, config.withDraft(draft.value))
                effectsFlow.emit(EqualizerEffect.ShowMessage(R.string.eq_profile_saved))
            }
        }

        fun showManageProfiles() {
            manageProfilesVisible.value = true
        }

        fun dismissManageProfiles() {
            manageProfilesVisible.value = false
        }

        fun applyProfile(profileId: String) {
            val profile = findProfile(profileId) ?: return
            draft.value = EqualizerDraft()
            manageProfilesVisible.value = false
            launchUpdate { manageProfiles.apply(profile) }
        }

        fun deleteProfile(profileId: String) = launchUpdate { manageProfiles.delete(profileId) }

        fun requestImport() {
            effectsFlow.tryEmit(EqualizerEffect.OpenImportDocument)
        }

        fun importProfiles(uri: Uri) =
            launchUpdate(errorMessageResId = R.string.eq_import_failed) {
                val count = manageProfiles.import(uri)
                draft.value = EqualizerDraft()
                effectsFlow.emit(EqualizerEffect.ShowMessage(R.string.eq_import_success, count))
            }

        fun requestExport(profileId: String) {
            val profile = findProfile(profileId) ?: return
            pendingExportProfileId = profileId
            val safeName = profile.name.ifBlank { "equalizer" }.replace(UNSAFE_FILE_CHARACTERS, "_")
            effectsFlow.tryEmit(EqualizerEffect.CreateExportDocument("$safeName-eq.json"))
        }

        fun exportProfile(uri: Uri) {
            val profileId = pendingExportProfileId.also { pendingExportProfileId = null } ?: return
            val profile = findProfile(profileId) ?: return
            launchUpdate {
                manageProfiles.export(uri, profile)
                effectsFlow.emit(EqualizerEffect.ShowMessage(R.string.eq_export_success))
            }
        }

        private fun findProfile(profileId: String): EqProfile? = configuration?.profiles?.firstOrNull { it.id == profileId }

        private fun launchUpdate(
            errorMessageResId: Int = R.string.error_unknown,
            block: suspend () -> Unit,
        ): Job =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    block()
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    effectsFlow.emit(EqualizerEffect.ShowMessage(errorMessageResId))
                }
            }

        private companion object {
            val UNSAFE_FILE_CHARACTERS = Regex("[^a-zA-Z0-9._-]")
        }
    }

private fun EqualizerConfiguration.withDraft(draft: EqualizerDraft): EqualizerConfiguration =
    copy(
        settings =
            settings.copy(
                bandLevelsMb = draft.bandLevelsMb ?: settings.bandLevelsMb,
                outputGainMb = draft.outputGainMb ?: settings.outputGainMb,
                bassBoostStrength = draft.bassBoostStrength ?: settings.bassBoostStrength,
                virtualizerStrength = draft.virtualizerStrength ?: settings.virtualizerStrength,
            ),
    )

private fun EqualizerConfiguration.toUiModel(
    draft: EqualizerDraft,
    saveDialog: SaveEqualizerProfileUiModel,
    manageProfilesVisible: Boolean,
): EqualizerUiModel {
    val capabilities = requireNotNull(capabilities)
    val settings = withDraft(draft).settings
    val bandLevels = resampleLevels(settings.bandLevelsMb, capabilities.bandCount)
    val toneModels =
        EqualizerTone.entries.map { tone ->
            val indices = equalizerToneIndices(tone, capabilities.centerFreqHz, capabilities.bandCount)
            val average = indices.sumOf { bandLevels[it] } / indices.size
            EqualizerToneUiModel(tone, draft.toneLevelsMb[tone] ?: average)
        }
    val presets =
        listOf(EqualizerPresetUiModel("flat", null, selectedProfileId == "flat")) +
            capabilities.systemPresets.mapIndexed { index, name ->
                val id = "system:$index"
                EqualizerPresetUiModel(id, name, selectedProfileId == id)
            }
    return EqualizerUiModel(
        enabled = settings.enabled,
        controlMode = controlMode,
        selectedProfileId = selectedProfileId,
        presets = EqualizerPresetUiModels(presets),
        tones = EqualizerToneUiModels(toneModels),
        bands =
            EqualizerBandUiModels(
                List(capabilities.bandCount) { index ->
                    EqualizerBandUiModel(index, capabilities.centerFreqHz.getOrElse(index) { 0 }, bandLevels[index])
                },
            ),
        minimumBandLevelMb = capabilities.minBandLevelMb,
        maximumBandLevelMb = maxOf(capabilities.maxBandLevelMb, capabilities.minBandLevelMb + 1),
        outputGainEnabled = settings.outputGainEnabled,
        outputGainMb = settings.outputGainMb,
        bassBoostEnabled = settings.bassBoostEnabled,
        bassBoostStrength = settings.bassBoostStrength,
        virtualizerEnabled = settings.virtualizerEnabled,
        virtualizerStrength = settings.virtualizerStrength,
        autoHeadroomEnabled = settings.autoHeadroomEnabled,
        profiles =
            EqualizerProfileUiModels(
                profiles.map { profile ->
                    EqualizerProfileUiModel(profile.id, profile.name, selectedProfileId == "profile:${profile.id}")
                },
            ),
        saveProfileDialog = saveDialog,
        manageProfilesVisible = manageProfilesVisible,
    )
}
