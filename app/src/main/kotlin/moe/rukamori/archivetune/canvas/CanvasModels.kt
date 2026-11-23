/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.canvas

import androidx.compose.runtime.Immutable

@Immutable
data class CanvasConfiguration(
    val enabled: Boolean = false,
    val source: CanvasSource = CanvasSource.ALL,
    val wifiOnly: Boolean = false,
    val cacheLimitMb: Int = 256,
    val lowDataMode: Boolean = false,
)

@Immutable
data class CanvasConnectivity(
    val online: Boolean = false,
    val wifi: Boolean = false,
    val metered: Boolean = true,
)

@Immutable
data class CanvasPolicy(
    val configuration: CanvasConfiguration = CanvasConfiguration(),
    val connectivity: CanvasConnectivity = CanvasConnectivity(),
    val ready: Boolean = false,
    val configurationError: Boolean = false,
) {
    val networkAllowed: Boolean
        get() = ready && configuration.enabled && connectivity.online &&
            (!configuration.wifiOnly || connectivity.wifi) &&
            (!configuration.lowDataMode || !connectivity.metered)
}

@Immutable
data class CanvasVideo(
    val source: CanvasSource,
    val static: String?,
    val animated: String?,
    val videoUrl: String?,
    val animatedVertical: String?,
    val videoUrlVertical: String?,
)

enum class CanvasHealth {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
    NOT_SELECTED,
    NOT_CONNECTED,
    DISABLED,
    OFFLINE,
    WIFI_REQUIRED,
    LOW_DATA_MODE,
}

@Immutable
data class CanvasHealthStatus(
    val betterLyrics: CanvasHealth = CanvasHealth.NOT_CHECKED,
    val appleMusic: CanvasHealth = CanvasHealth.NOT_CHECKED,
    val tidal: CanvasHealth = CanvasHealth.NOT_CHECKED,
    val spotify: CanvasHealth = CanvasHealth.NOT_CHECKED,
    val modules: CanvasHealth = CanvasHealth.NOT_CHECKED,
) {
    val checking: Boolean
        get() = betterLyrics == CanvasHealth.CHECKING || appleMusic == CanvasHealth.CHECKING ||
            tidal == CanvasHealth.CHECKING || spotify == CanvasHealth.CHECKING || modules == CanvasHealth.CHECKING
}
