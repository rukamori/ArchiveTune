/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.models.ActiveOutputDevice
import moe.rukamori.archivetune.models.PlayerOutputDevice

/**
 * Resolves the currently active audio output device and exposes it as a [StateFlow] for the
 * player UI (V7/V8 device pill).
 *
 * IMPORTANT — this resolver is NOT self-updating. It does not register any
 * [android.media.AudioDeviceCallback] and will not observe device changes on its own.
 * Callers (e.g. [MusicService]) must explicitly call [refresh] whenever a change is known
 * or suspected:
 *  - On device connect/disconnect — driven by the AudioDeviceCallback already registered
 *    in [MusicService], which calls [refresh] from its `onAudioOutputDeviceChanged` hook.
 *  - On demand (e.g. after the user opens the system output switcher) — via [MusicService]'s
 *    `refreshActiveDevice()` wrapper, used by the action-triggered polling in Queue.kt.
 *
 * The [activeAudioDevice] flow is accurate at the moment [refresh] was last called, but it
 * will go stale if no one calls [refresh] after a routing change. Do not add new consumers
 * of this flow without also wiring a refresh trigger.
 */
class AudioOutputResolver(private val audioManager: AudioManager) {

    /**
     * Framework [android.media.AudioAttributes] for USAGE_MEDIA / CONTENT_TYPE_MUSIC, used to
     * query the system's actual media routing. Built once and cached. Note: this is distinct
     * from media3's `androidx.media3.common.AudioAttributes` used for playback; the framework
     * query API requires the framework type.
     */
    private val mediaQueryAttributes = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val _activeAudioDevice = MutableStateFlow(
        ActiveOutputDevice(PlayerOutputDevice.Unknown, PlayerOutputDevice.Unknown.defaultName)
    )

    /**
     * The currently active audio output device. See the class kdoc: this is only updated when
     * [refresh] is invoked.
     */
    val activeAudioDevice = _activeAudioDevice.asStateFlow()

    /**
     * Forces an immediate re-resolve of the active output device and emits the new value.
     * See the class kdoc for why this must be called explicitly.
     */
    fun refresh() {
        _activeAudioDevice.value = resolveActiveDevice()
    }

    /**
     * Best-effort detection of the current output device.
     *
     * On API 33+ (TIRAMISU) this queries the system for the actual media routing via
     * [getAudioDevicesForAttributes] — the device(s) the system would route a USAGE_MEDIA
     * AudioTrack to. This is ground-truth and respects manual overrides the user makes via
     * the system output switcher, even when other devices remain connected.
     *
     * On API < 33, or as a fallback if the API returns nothing, it falls back to a
     * connect-status priority heuristic (Bluetooth > USB > Headset > HDMI > Speaker).
     *
     * NOTE on reactivity: this query is accurate *when called*, but Android exposes no
     * public, reliable *reactive* API for media-route changes:
     *  - [android.media.MediaRouter2.ControllerCallback] exists but is documented as
     *    "not necessarily reliable in all situations" (see androidx/media#2080).
     *  - [getAudioDevicesForAttributes] has no associated change-listener.
     *  - ExoPlayer's internal `DefaultAudioSink.routedDevice` is the true source of truth
     *    but is not exposed publicly in media3 1.10.1.
     *
     * Consequently the pill is correct whenever it (re)renders, but will NOT auto-update on
     * a manual route switch that isn't accompanied by a device connect/disconnect event.
     */
    private fun resolveActiveDevice(): ActiveOutputDevice {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveActiveDeviceApi33() ?: resolveActiveDeviceHeuristic()
        } else {
            resolveActiveDeviceHeuristic()
        }

        // Name/type mapping lives here in the dispatcher, NOT in the source-specific helpers
        // below, so the productName-fallback logic exists in exactly one place.
        val type = PlayerOutputDevice.from(device)
        val name = if (type.isProduct) {
            device?.productName?.toString()?.takeUnless { it.isBlank() } ?: type.defaultName
        } else {
            type.defaultName
        }
        return ActiveOutputDevice(type, name)
    }

    /**
     * Ground-truth query: asks the system which device(s) a USAGE_MEDIA AudioTrack would be
     * routed to right now. Respects the user's manual selection in the system output switcher.
     * API 33+ (TIRAMISU) only.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun resolveActiveDeviceApi33(): AudioDeviceInfo? {
        return audioManager
            .getAudioDevicesForAttributes(mediaQueryAttributes)
            .firstOrNull { it.isSink }
    }

    /**
     * Fallback heuristic: connect-status + a fixed priority order. Used on API < 33, and as
     * a safety net when [resolveActiveDeviceApi33] returns an empty list.
     *
     * Caveat: this reflects what is *connected*, not necessarily what is *routing*. If a
     * device is connected but audio is routed elsewhere, the heuristic may report the
     * connected device rather than the actively-routed one. This priority order is also
     * OEM/version-dependent in practice and may not match every device's actual routing.
     */
    private fun resolveActiveDeviceHeuristic(): AudioDeviceInfo? {
        val sinks = audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }

        return sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_HDMI ||
                    it.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
                    it.type == AudioDeviceInfo.TYPE_HDMI_EARC
            }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE ||
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
    }
}
