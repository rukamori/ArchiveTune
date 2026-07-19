/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.equalizer

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.EqualizerAutoHeadroomEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBandLevelsMbKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostEnabledKey
import moe.rukamori.archivetune.constants.EqualizerBassBoostStrengthKey
import moe.rukamori.archivetune.constants.EqualizerControlModeKey
import moe.rukamori.archivetune.constants.EqualizerCustomProfilesJsonKey
import moe.rukamori.archivetune.constants.EqualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainEnabledKey
import moe.rukamori.archivetune.constants.EqualizerOutputGainMbKey
import moe.rukamori.archivetune.constants.EqualizerSelectedProfileIdKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerEnabledKey
import moe.rukamori.archivetune.constants.EqualizerVirtualizerStrengthKey
import moe.rukamori.archivetune.playback.EqCapabilities
import moe.rukamori.archivetune.playback.EqProfile
import moe.rukamori.archivetune.playback.EqProfilesPayload
import moe.rukamori.archivetune.playback.EqSettings
import moe.rukamori.archivetune.playback.EqualizerJson
import moe.rukamori.archivetune.playback.EqualizerPlaybackController
import moe.rukamori.archivetune.utils.dataStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerRepositorySnapshot(
    val controlMode: EqualizerControlMode,
    val selectedProfileId: String,
    val settings: EqSettings,
    val capabilities: EqCapabilities?,
    val profiles: List<EqProfile>,
)

@Singleton
class EqualizerRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playbackController: EqualizerPlaybackController,
    ) {
        fun observe(): Flow<EqualizerRepositorySnapshot> =
            combine(
                context.dataStore.data.map { preferences ->
                    val profiles = decodeProfiles(preferences[EqualizerCustomProfilesJsonKey])
                    EqualizerRepositorySnapshot(
                        controlMode = EqualizerControlMode.fromStorage(preferences[EqualizerControlModeKey]),
                        selectedProfileId = preferences[EqualizerSelectedProfileIdKey] ?: FLAT_PROFILE_ID,
                        settings =
                            EqSettings(
                                enabled = preferences[EqualizerEnabledKey] ?: false,
                                bandLevelsMb = decodeLevels(preferences[EqualizerBandLevelsMbKey]),
                                outputGainEnabled = preferences[EqualizerOutputGainEnabledKey] ?: false,
                                outputGainMb = preferences[EqualizerOutputGainMbKey] ?: 0,
                                bassBoostEnabled = preferences[EqualizerBassBoostEnabledKey] ?: false,
                                bassBoostStrength = (preferences[EqualizerBassBoostStrengthKey] ?: 0).coerceIn(0, 1000),
                                virtualizerEnabled = preferences[EqualizerVirtualizerEnabledKey] ?: false,
                                virtualizerStrength = (preferences[EqualizerVirtualizerStrengthKey] ?: 0).coerceIn(0, 1000),
                                autoHeadroomEnabled = preferences[EqualizerAutoHeadroomEnabledKey] ?: false,
                            ),
                        capabilities = null,
                        profiles = profiles,
                    )
                },
                playbackController.capabilities,
            ) { snapshot, capabilities ->
                snapshot.copy(capabilities = capabilities)
            }.flowOn(Dispatchers.IO)

        suspend fun setEnabled(enabled: Boolean) = edit { it[EqualizerEnabledKey] = enabled }

        suspend fun setControlMode(mode: EqualizerControlMode) = edit { it[EqualizerControlModeKey] = mode.storageValue }

        suspend fun updateBandLevels(levelsMb: List<Int>) =
            edit {
                it[EqualizerBandLevelsMbKey] = EqualizerJson.json.encodeToString(levelsMb)
                it[EqualizerSelectedProfileIdKey] = MANUAL_PROFILE_ID
            }

        suspend fun setOutputGainEnabled(enabled: Boolean) = editManual { it[EqualizerOutputGainEnabledKey] = enabled }

        suspend fun setOutputGainMb(gainMb: Int) = editManual { it[EqualizerOutputGainMbKey] = gainMb.coerceIn(-1500, 1500) }

        suspend fun setBassBoostEnabled(enabled: Boolean) = editManual { it[EqualizerBassBoostEnabledKey] = enabled }

        suspend fun setBassBoostStrength(strength: Int) = editManual { it[EqualizerBassBoostStrengthKey] = strength.coerceIn(0, 1000) }

        suspend fun setVirtualizerEnabled(enabled: Boolean) = editManual { it[EqualizerVirtualizerEnabledKey] = enabled }

        suspend fun setVirtualizerStrength(strength: Int) = editManual { it[EqualizerVirtualizerStrengthKey] = strength.coerceIn(0, 1000) }

        suspend fun setAutoHeadroomEnabled(enabled: Boolean) = editManual { it[EqualizerAutoHeadroomEnabledKey] = enabled }

        suspend fun applyProfile(profile: EqProfile) {
            context.dataStore.edit { preferences ->
                writeProfileSettings(preferences, profile)
                preferences[EqualizerSelectedProfileIdKey] = "$PROFILE_PREFIX${profile.id}"
            }
        }

        suspend fun saveProfile(
            name: String,
            centerFrequenciesHz: List<Int>,
            settings: EqSettings,
        ): EqProfile {
            val profile =
                EqProfile(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    bandCenterFreqHz = centerFrequenciesHz,
                    bandLevelsMb = settings.bandLevelsMb,
                    outputGainMb = settings.outputGainMb,
                    outputGainEnabled = settings.outputGainEnabled,
                    bassBoostStrength = settings.bassBoostStrength,
                    bassBoostEnabled = settings.bassBoostEnabled,
                    virtualizerStrength = settings.virtualizerStrength,
                    virtualizerEnabled = settings.virtualizerEnabled,
                    autoHeadroomEnabled = settings.autoHeadroomEnabled,
                )
            context.dataStore.edit { preferences ->
                val profiles = decodeProfiles(preferences[EqualizerCustomProfilesJsonKey])
                preferences[EqualizerCustomProfilesJsonKey] = encodeProfiles(profiles + profile)
                preferences[EqualizerSelectedProfileIdKey] = "$PROFILE_PREFIX${profile.id}"
            }
            return profile
        }

        suspend fun deleteProfile(profileId: String) {
            context.dataStore.edit { preferences ->
                val profiles = decodeProfiles(preferences[EqualizerCustomProfilesJsonKey])
                preferences[EqualizerCustomProfilesJsonKey] = encodeProfiles(profiles.filterNot { it.id == profileId })
                if (preferences[EqualizerSelectedProfileIdKey] == "$PROFILE_PREFIX$profileId") {
                    preferences[EqualizerSelectedProfileIdKey] = MANUAL_PROFILE_ID
                }
            }
        }

        suspend fun importProfiles(uri: Uri): Int =
            withContext(Dispatchers.IO) {
                val raw =
                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("Unable to open equalizer profile")
                val imported = decodeImport(raw)
                require(imported.isNotEmpty())
                context.dataStore.edit { preferences ->
                    val existing = decodeProfiles(preferences[EqualizerCustomProfilesJsonKey])
                    val existingIds = existing.mapTo(mutableSetOf()) { it.id }
                    val normalized =
                        imported.map { profile ->
                            val id = profile.id.takeIf { it.isNotBlank() && existingIds.add(it) } ?: uniqueId(existingIds)
                            profile.copy(id = id, name = profile.name.trim())
                        }
                    preferences[EqualizerCustomProfilesJsonKey] = encodeProfiles(existing + normalized)
                    writeProfileSettings(preferences, normalized.first())
                    preferences[EqualizerSelectedProfileIdKey] = "$PROFILE_PREFIX${normalized.first().id}"
                }
                imported.size
            }

        suspend fun exportProfile(
            uri: Uri,
            profile: EqProfile,
        ) = withContext(Dispatchers.IO) {
            val raw = EqualizerJson.json.encodeToString(EqProfilesPayload(listOf(profile)))
            val output = context.contentResolver.openOutputStream(uri) ?: error("Unable to open equalizer profile")
            output.bufferedWriter().use { it.write(raw) }
        }

        fun applyFlatPreset(): Boolean = playbackController.applyFlatPreset()

        fun applySystemPreset(index: Int): Boolean = playbackController.applySystemPreset(index)

        private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
            withContext(Dispatchers.IO) { context.dataStore.edit(block) }
        }

        private suspend fun editManual(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
            edit { preferences ->
                block(preferences)
                preferences[EqualizerSelectedProfileIdKey] = MANUAL_PROFILE_ID
            }
        }

        private fun writeProfileSettings(
            preferences: androidx.datastore.preferences.core.MutablePreferences,
            profile: EqProfile,
        ) {
            preferences[EqualizerEnabledKey] = true
            preferences[EqualizerBandLevelsMbKey] = EqualizerJson.json.encodeToString(profile.bandLevelsMb)
            preferences[EqualizerOutputGainMbKey] = profile.outputGainMb.coerceIn(-1500, 1500)
            preferences[EqualizerOutputGainEnabledKey] = profile.outputGainEnabled ?: (profile.outputGainMb != 0)
            preferences[EqualizerBassBoostStrengthKey] = profile.bassBoostStrength.coerceIn(0, 1000)
            preferences[EqualizerBassBoostEnabledKey] = profile.bassBoostEnabled ?: (profile.bassBoostStrength != 0)
            preferences[EqualizerVirtualizerStrengthKey] = profile.virtualizerStrength.coerceIn(0, 1000)
            preferences[EqualizerVirtualizerEnabledKey] = profile.virtualizerEnabled ?: (profile.virtualizerStrength != 0)
            preferences[EqualizerAutoHeadroomEnabledKey] = profile.autoHeadroomEnabled
        }

        private fun decodeLevels(raw: String?): List<Int> =
            raw
                ?.takeIf(
                    String::isNotBlank,
                )?.let { runCatching { EqualizerJson.json.decodeFromString<List<Int>>(it) }.getOrNull() }
                .orEmpty()

        private fun decodeProfiles(raw: String?): List<EqProfile> =
            raw
                ?.takeIf(String::isNotBlank)
                ?.let { runCatching { EqualizerJson.json.decodeFromString<EqProfilesPayload>(it).profiles }.getOrNull() }
                .orEmpty()

        private fun decodeImport(raw: String): List<EqProfile> =
            runCatching { EqualizerJson.json.decodeFromString<EqProfilesPayload>(raw).profiles }.getOrNull()
                ?: runCatching { EqualizerJson.json.decodeFromString<List<EqProfile>>(raw) }.getOrDefault(emptyList())

        private fun encodeProfiles(profiles: List<EqProfile>): String =
            EqualizerJson.json.encodeToString(EqProfilesPayload(profiles.distinctBy(EqProfile::id).sortedBy { it.name.lowercase() }))

        private fun uniqueId(existingIds: MutableSet<String>): String =
            generateSequence { UUID.randomUUID().toString() }.first(existingIds::add)

        private companion object {
            const val FLAT_PROFILE_ID = "flat"
            const val MANUAL_PROFILE_ID = "manual"
            const val PROFILE_PREFIX = "profile:"
        }
    }
