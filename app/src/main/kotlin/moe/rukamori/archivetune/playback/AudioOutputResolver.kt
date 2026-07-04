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

class AudioOutputResolver(private val audioManager: AudioManager) {

    private val mediaQueryAttributes = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val _activeAudioDevice = MutableStateFlow(
        ActiveOutputDevice(PlayerOutputDevice.Unknown, PlayerOutputDevice.Unknown.defaultName)
    )

    val activeAudioDevice = _activeAudioDevice.asStateFlow()

    fun refresh() {
        _activeAudioDevice.value = resolveActiveDevice()
    }

    private fun resolveActiveDevice(): ActiveOutputDevice {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveActiveDeviceApi33() ?: resolveActiveDeviceHeuristic()
        } else {
            resolveActiveDeviceHeuristic()
        }

        val type = PlayerOutputDevice.from(device)
        val name = if (type.isProduct) {
            device?.productName?.toString()?.takeUnless { it.isBlank() } ?: type.defaultName
        } else {
            type.defaultName
        }
        return ActiveOutputDevice(type, name)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun resolveActiveDeviceApi33(): AudioDeviceInfo? {
        return audioManager
            .getAudioDevicesForAttributes(mediaQueryAttributes)
            .firstOrNull { it.isSink }
    }

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
