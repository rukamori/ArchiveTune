/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.models

import android.media.AudioDeviceInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

enum class PlayerOutputDevice(
    val imageVector: ImageVector,
    val isProduct: Boolean,
    val defaultName: String,
) {
    Bluetooth(Icons.Rounded.Bluetooth, isProduct = true, defaultName = "Bluetooth"),
    Usb(Icons.Rounded.Usb, isProduct = true, defaultName = "USB"),
    Headset(Icons.Rounded.Headphones, isProduct = false, defaultName = "Headset"),
    Hdmi(Icons.Rounded.Cable, isProduct = false, defaultName = "HDMI"),
    BuiltinSpeaker(Icons.Rounded.Speaker, isProduct = false, defaultName = "Speaker"),
    Unknown(Icons.Rounded.Speaker, isProduct = false, defaultName = "Speaker");

    companion object {
        fun from(info: AudioDeviceInfo?): PlayerOutputDevice = when (info?.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID -> Bluetooth

            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> Usb

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> Headset

            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> Hdmi

            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> BuiltinSpeaker

            else -> Unknown
        }
    }
}

@Immutable
data class ActiveOutputDevice(
    val type: PlayerOutputDevice,
    val name: String,
)
