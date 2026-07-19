/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EqProfile(
    val id: String,
    val name: String,
    val bandCenterFreqHz: List<Int> = emptyList(),
    val bandLevelsMb: List<Int> = emptyList(),
    val outputGainMb: Int = 0,
    val outputGainEnabled: Boolean? = null,
    val bassBoostStrength: Int = 0,
    val bassBoostEnabled: Boolean? = null,
    val virtualizerStrength: Int = 0,
    val virtualizerEnabled: Boolean? = null,
    val autoHeadroomEnabled: Boolean = false,
)

@Serializable
data class EqProfilesPayload(
    @SerialName("profiles")
    val profiles: List<EqProfile> = emptyList(),
)

data class EqCapabilities(
    val bandCount: Int,
    val minBandLevelMb: Int,
    val maxBandLevelMb: Int,
    val centerFreqHz: List<Int>,
    val systemPresets: List<String>,
)

data class EqSettings(
    val enabled: Boolean,
    val bandLevelsMb: List<Int>,
    val outputGainEnabled: Boolean,
    val outputGainMb: Int,
    val bassBoostEnabled: Boolean,
    val bassBoostStrength: Int,
    val virtualizerEnabled: Boolean,
    val virtualizerStrength: Int,
    val autoHeadroomEnabled: Boolean,
)

internal object EqualizerJson {
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
}
