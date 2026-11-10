/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/usb/UsbDirectDevices.kt, Copyright (C) 2026 BitChord Project,
 * adapted in part from Mornye (https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet.
 * SPDX-License-Identifier of the adapted source: LGPL-3.0-only
 */

package moe.rukamori.archivetune.playback.bitperfect.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import timber.log.Timber

internal class UsbDirectDevices(
    private val context: Context,
    private val onChanged: () -> Unit,
    private val onDetached: (Int) -> Unit,
) {
    val manager: UsbManager? = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val permissionAction = "${context.packageName}.USB_AUDIO_PERMISSION"
    private var receiverRegistered = false
    private var requestedDeviceId: Int? = null
    private val descriptors = HashMap<Int, ByteArray>()

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        synchronized(this@UsbDirectDevices) {
                            descriptors.remove(device.deviceId)
                            if (requestedDeviceId == device.deviceId) requestedDeviceId = null
                        }
                        onDetached(device.deviceId)
                        onChanged()
                    }

                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> onChanged()

                    permissionAction -> {
                        synchronized(this@UsbDirectDevices) { requestedDeviceId = null }
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        Timber.tag(TAG).i("USB permission for %s: %s", device.productName, granted)
                        if (granted) onChanged()
                    }
                }
            }
        }

    @Synchronized
    fun watch() {
        if (receiverRegistered) return
        val filter =
            IntentFilter(permissionAction).apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    fun audioDevice(): UsbDevice? {
        val usb = manager ?: return null
        return runCatching { usb.deviceList.values }
            .getOrDefault(emptyList())
            .sortedBy { it.deviceId }
            .firstOrNull { device ->
                (0 until device.interfaceCount).any { index ->
                    val usbInterface = device.getInterface(index)
                    usbInterface.interfaceClass == UsbConstants.USB_CLASS_AUDIO && usbInterface.interfaceSubclass == 2
                }
            }
    }

    fun hasPermission(device: UsbDevice): Boolean = manager?.hasPermission(device) == true

    @Synchronized
    fun requestPermission(force: Boolean) {
        val usb = manager ?: return
        val device = audioDevice() ?: return
        if (usb.hasPermission(device)) return
        if (!force && requestedDeviceId == device.deviceId) return
        requestedDeviceId = device.deviceId
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent =
            PendingIntent.getBroadcast(
                context,
                device.deviceId,
                Intent(permissionAction).setPackage(context.packageName),
                flags,
            )
        runCatching { usb.requestPermission(device, intent) }
            .onFailure { error ->
                requestedDeviceId = null
                Timber.tag(TAG).w(error, "USB permission request failed")
            }
    }

    @Synchronized
    fun rawDescriptors(device: UsbDevice): ByteArray? {
        descriptors[device.deviceId]?.let { return it }
        val usb = manager ?: return null
        if (!usb.hasPermission(device)) return null
        val connection = runCatching { usb.openDevice(device) }.getOrNull() ?: return null
        return try {
            connection.rawDescriptors?.also { descriptors[device.deviceId] = it }
        } finally {
            connection.close()
        }
    }

    private companion object {
        const val TAG = "UsbDirectDevices"
    }
}
