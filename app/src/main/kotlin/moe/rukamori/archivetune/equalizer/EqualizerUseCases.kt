/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.equalizer

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.playback.EqCapabilities
import moe.rukamori.archivetune.playback.EqProfile
import moe.rukamori.archivetune.playback.EqSettings
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor

enum class EqualizerControlMode(
    val storageValue: String,
) {
    BASIC("basic"),
    ADVANCED("advanced"),
    ;

    companion object {
        fun fromStorage(value: String?): EqualizerControlMode = entries.firstOrNull { it.storageValue == value } ?: BASIC
    }
}

enum class EqualizerTone {
    BASS,
    MIDRANGE,
    TREBLE,
}

data class EqualizerConfiguration(
    val controlMode: EqualizerControlMode,
    val selectedProfileId: String,
    val settings: EqSettings,
    val capabilities: EqCapabilities?,
    val profiles: List<EqProfile>,
)

class ObserveEqualizerUseCase
    @Inject
    constructor(
        private val repository: EqualizerRepository,
    ) {
        operator fun invoke(): Flow<EqualizerConfiguration> =
            repository.observe().map { snapshot ->
                val capabilities = snapshot.capabilities
                val normalizedLevels =
                    resampleLevels(
                        levelsMb = snapshot.settings.bandLevelsMb,
                        targetCount = capabilities?.bandCount ?: snapshot.settings.bandLevelsMb.size,
                    )
                EqualizerConfiguration(
                    controlMode = snapshot.controlMode,
                    selectedProfileId = snapshot.selectedProfileId,
                    settings = snapshot.settings.copy(bandLevelsMb = normalizedLevels),
                    capabilities = capabilities,
                    profiles = snapshot.profiles,
                )
            }
    }

class UpdateEqualizerUseCase
    @Inject
    constructor(
        private val repository: EqualizerRepository,
    ) {
        suspend fun setEnabled(enabled: Boolean) = repository.setEnabled(enabled)

        suspend fun setControlMode(mode: EqualizerControlMode) = repository.setControlMode(mode)

        suspend fun updateTone(
            tone: EqualizerTone,
            targetLevelMb: Int,
            configuration: EqualizerConfiguration,
        ) {
            repository.updateBandLevels(adjustToneBands(tone, targetLevelMb, configuration))
        }

        fun adjustToneBands(
            tone: EqualizerTone,
            targetLevelMb: Int,
            configuration: EqualizerConfiguration,
        ): List<Int> {
            val capabilities = configuration.capabilities ?: return configuration.settings.bandLevelsMb
            val current = resampleLevels(configuration.settings.bandLevelsMb, capabilities.bandCount).toMutableList()
            val indices = equalizerToneIndices(tone, capabilities.centerFreqHz, capabilities.bandCount)
            if (indices.isEmpty()) return current
            val average = indices.sumOf { current[it] } / indices.size
            val delta = targetLevelMb - average
            indices.forEach { index ->
                current[index] = (current[index] + delta).coerceIn(capabilities.minBandLevelMb, capabilities.maxBandLevelMb)
            }
            return current
        }

        suspend fun updateBand(
            index: Int,
            levelMb: Int,
            configuration: EqualizerConfiguration,
        ) {
            val capabilities = configuration.capabilities ?: return
            if (index !in 0 until capabilities.bandCount) return
            val levels = resampleLevels(configuration.settings.bandLevelsMb, capabilities.bandCount).toMutableList()
            levels[index] = levelMb.coerceIn(capabilities.minBandLevelMb, capabilities.maxBandLevelMb)
            repository.updateBandLevels(levels)
        }

        suspend fun resetBands(configuration: EqualizerConfiguration) {
            val count = configuration.capabilities?.bandCount ?: return
            repository.updateBandLevels(List(count) { 0 })
        }

        suspend fun updateBandLevels(levelsMb: List<Int>) = repository.updateBandLevels(levelsMb)

        suspend fun setOutputGainEnabled(enabled: Boolean) = repository.setOutputGainEnabled(enabled)

        suspend fun setOutputGainMb(value: Int) = repository.setOutputGainMb(value)

        suspend fun setBassBoostEnabled(enabled: Boolean) = repository.setBassBoostEnabled(enabled)

        suspend fun setBassBoostStrength(value: Int) = repository.setBassBoostStrength(value)

        suspend fun setVirtualizerEnabled(enabled: Boolean) = repository.setVirtualizerEnabled(enabled)

        suspend fun setVirtualizerStrength(value: Int) = repository.setVirtualizerStrength(value)

        suspend fun setAutoHeadroomEnabled(enabled: Boolean) = repository.setAutoHeadroomEnabled(enabled)
    }

class ManageEqualizerProfilesUseCase
    @Inject
    constructor(
        private val repository: EqualizerRepository,
    ) {
        suspend fun save(
            name: String,
            configuration: EqualizerConfiguration,
        ): EqProfile =
            repository.saveProfile(
                name = name.trim(),
                centerFrequenciesHz = configuration.capabilities?.centerFreqHz.orEmpty(),
                settings = configuration.settings,
            )

        suspend fun apply(profile: EqProfile) = repository.applyProfile(profile)

        suspend fun delete(profileId: String) = repository.deleteProfile(profileId)

        suspend fun import(uri: Uri): Int = repository.importProfiles(uri)

        suspend fun export(
            uri: Uri,
            profile: EqProfile,
        ) = repository.exportProfile(uri, profile)
    }

class ApplyEqualizerPresetUseCase
    @Inject
    constructor(
        private val repository: EqualizerRepository,
    ) {
        operator fun invoke(presetId: String): Boolean =
            when {
                presetId == "flat" -> {
                    repository.applyFlatPreset()
                }

                presetId.startsWith("system:") -> {
                    presetId.removePrefix("system:").toIntOrNull()?.let(repository::applySystemPreset)
                        ?: false
                }

                else -> {
                    false
                }
            }
    }

internal fun resampleLevels(
    levelsMb: List<Int>,
    targetCount: Int,
): List<Int> {
    if (targetCount <= 0) return emptyList()
    if (levelsMb.isEmpty()) return List(targetCount) { 0 }
    if (levelsMb.size == targetCount) return levelsMb
    if (targetCount == 1) return listOf(levelsMb.average().toInt())
    val lastIndex = levelsMb.lastIndex.toFloat().coerceAtLeast(1f)
    return List(targetCount) { index ->
        val position = index * lastIndex / (targetCount - 1)
        val lower = floor(position).toInt().coerceIn(0, levelsMb.lastIndex)
        val upper = ceil(position).toInt().coerceIn(0, levelsMb.lastIndex)
        (levelsMb[lower] + ((levelsMb[upper] - levelsMb[lower]) * (position - lower))).toInt()
    }
}

internal fun equalizerToneIndices(
    tone: EqualizerTone,
    frequenciesHz: List<Int>,
    bandCount: Int,
): List<Int> {
    if (bandCount <= 0) return emptyList()
    val frequencyMatches =
        frequenciesHz
            .takeIf { it.size == bandCount && it.any { frequency -> frequency > 0 } }
            ?.indices
            ?.filter { index ->
                when (tone) {
                    EqualizerTone.BASS -> frequenciesHz[index] <= 250
                    EqualizerTone.MIDRANGE -> frequenciesHz[index] in 251..4_000
                    EqualizerTone.TREBLE -> frequenciesHz[index] > 4_000
                }
            }.orEmpty()
    if (frequencyMatches.isNotEmpty()) return frequencyMatches

    val firstBoundary = ceil(bandCount / 3.0).toInt()
    val secondBoundary = ceil(bandCount * 2 / 3.0).toInt()
    val range =
        when (tone) {
            EqualizerTone.BASS -> 0 until firstBoundary
            EqualizerTone.MIDRANGE -> firstBoundary until secondBoundary
            EqualizerTone.TREBLE -> secondBoundary until bandCount
        }.toList()
    if (range.isNotEmpty()) return range
    return listOf(
        when (tone) {
            EqualizerTone.BASS -> 0
            EqualizerTone.MIDRANGE -> (bandCount - 1) / 2
            EqualizerTone.TREBLE -> bandCount - 1
        },
    )
}
