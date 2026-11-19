/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/BitPerfectOutput.kt, Copyright (C) 2026 BitChord Project,
 * licensed under the Apache License, Version 2.0.
 */

package moe.rukamori.archivetune.playback.bitperfect

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import moe.rukamori.archivetune.constants.UsbBitPerfectDriver
import moe.rukamori.archivetune.playback.bitperfect.usb.UsbDescriptors
import moe.rukamori.archivetune.playback.bitperfect.usb.UsbDirectDevices
import moe.rukamori.archivetune.playback.bitperfect.usb.UsbDirectTransport
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

enum class BitPerfectFallback {
    NONE,
    UNAVAILABLE,
    NO_HARDWARE_VOLUME,
}

enum class BitPerfectEvent {
    OUTPUT_CHANGED,
    DAC_DETACHED,
}

@Immutable
data class BitPerfectStatus(
    val dacName: String? = null,
    val permissionRequired: Boolean = false,
    val active: Boolean = false,
    val exact: Boolean = true,
    val sampleRateHz: Int = 0,
    val outputBits: Int = 0,
    val fallback: BitPerfectFallback = BitPerfectFallback.NONE,
)

@Singleton
class BitPerfectOutput
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        private val usbDevices =
            UsbDirectDevices(
                context = context,
                onChanged = ::onUsbDevicesChanged,
                onDetached = ::onUsbDeviceDetached,
            )
        private val volumeExecutor = Executors.newSingleThreadExecutor { Thread(it, "ArchiveTune-USB-volume") }
        private val volumeUpdatePending = AtomicBoolean(false)

        private val _status = MutableStateFlow(BitPerfectStatus())
        val status: StateFlow<BitPerfectStatus> = _status.asStateFlow()

        private val _events = MutableSharedFlow<BitPerfectEvent>(extraBufferCapacity = 8)
        val events: SharedFlow<BitPerfectEvent> = _events.asSharedFlow()

        @Volatile
        var enabled = false
            private set

        @Volatile
        private var driver = UsbBitPerfectDriver.ANDROID

        @Volatile
        private var allowFixedVolume = false

        @Volatile
        private var volumeFraction = DEFAULT_VOLUME_FRACTION

        @Volatile
        private var directReleasedAtMs = 0L

        private var owner: Any? = null
        private var activeTransport: BitPerfectTransport? = null
        private val failedFormats = HashSet<String>()
        private var failedDeviceId = NO_DEVICE

        val ownsUsbDisconnectHandling: Boolean
            get() {
                if (!enabled || driver != UsbBitPerfectDriver.DIRECT) return false
                val device = usbDevices.audioDevice() ?: return false
                return usbDevices.hasPermission(device)
            }

        internal val silenced: Boolean
            get() = volumeFraction <= 0f && driver == UsbBitPerfectDriver.DIRECT

        @Synchronized
        fun configure(
            enabled: Boolean,
            driver: UsbBitPerfectDriver,
            allowFixedVolume: Boolean,
        ): Boolean {
            val effectiveDriver = if (isMixerSupported) driver else UsbBitPerfectDriver.DIRECT
            if (this.enabled == enabled && this.driver == effectiveDriver && this.allowFixedVolume == allowFixedVolume) {
                return false
            }
            this.enabled = enabled
            this.driver = effectiveDriver
            this.allowFixedVolume = allowFixedVolume
            failedFormats.clear()
            _status.update { it.copy(fallback = BitPerfectFallback.NONE) }
            if (enabled && effectiveDriver == UsbBitPerfectDriver.DIRECT) usbDevices.watch()
            refreshDevice()
            return true
        }

        fun refreshDevice() {
            var name: String? = null
            var permissionRequired = false
            if (enabled && driver == UsbBitPerfectDriver.DIRECT) {
                usbDevices.audioDevice()?.let { device ->
                    name = device.productName?.takeIf { it.isNotBlank() } ?: DEFAULT_DEVICE_NAME
                    permissionRequired = !usbDevices.hasPermission(device)
                }
                if (permissionRequired) usbDevices.requestPermission(force = false)
            } else if (enabled) {
                name = audioManager.usbOutputDevice()?.usbDisplayName()
            }
            _status.update { current ->
                if (current.dacName == name && current.permissionRequired == permissionRequired) {
                    current
                } else {
                    current.copy(
                        dacName = name,
                        permissionRequired = permissionRequired,
                        fallback = BitPerfectFallback.NONE,
                    )
                }
            }
        }

        fun requestUsbPermission() {
            if (enabled && driver == UsbBitPerfectDriver.DIRECT) usbDevices.requestPermission(force = true)
        }

        fun setDeviceVolume(fraction: Float) {
            volumeFraction = fraction.coerceIn(0f, 1f)
            if (!volumeUpdatePending.compareAndSet(false, true)) return
            volumeExecutor.execute {
                volumeUpdatePending.set(false)
                val transport = synchronized(this) { activeTransport as? UsbDirectTransport } ?: return@execute
                runCatching { transport.setVolumeFraction(volumeFraction) }
                    .onFailure { Timber.tag(TAG).w(it, "USB volume change failed") }
            }
        }

        internal fun normalOutputPending(): Boolean {
            val releasedAt = directReleasedAtMs
            if (releasedAt == 0L) return false
            if (SystemClock.elapsedRealtime() - releasedAt > DIRECT_HANDBACK_MS) {
                directReleasedAtMs = 0L
                return false
            }
            val returned = audioManager.usbOutputDevice() != null
            if (returned) directReleasedAtMs = 0L
            return !returned
        }

        @Synchronized
        internal fun canOpen(
            sampleRate: Int,
            channelCount: Int,
            sourceBits: Int,
            requester: Any,
            reportUnsupported: Boolean,
        ): Boolean {
            if (!enabled) return false
            if (owner != null && owner !== requester) return false
            val key = formatKey(sampleRate, channelCount, sourceBits)
            val supported =
                if (driver == UsbBitPerfectDriver.DIRECT) {
                    val device = usbDevices.audioDevice() ?: return false
                    if (!usbDevices.hasPermission(device)) return false
                    val raw = usbDevices.rawDescriptors(device) ?: return false
                    key !in failuresFor(device.deviceId) &&
                        UsbDescriptors.plans(raw, sampleRate, channelCount, sourceBits).isNotEmpty()
                } else {
                    if (!isMixerSupported) return false
                    val device = audioManager.usbOutputDevice() ?: return false
                    key !in failuresFor(device.id) &&
                        UsbBitPerfectTransport.matchingFormat(audioManager, device, sampleRate, channelCount, sourceBits) != null
                }
            if (!supported && reportUnsupported) {
                _status.update { current ->
                    if (current.active || current.fallback != BitPerfectFallback.NONE) {
                        current
                    } else {
                        current.copy(fallback = BitPerfectFallback.UNAVAILABLE)
                    }
                }
            }
            return supported
        }

        @Synchronized
        internal fun open(
            sampleRate: Int,
            channelCount: Int,
            sourceBits: Int,
            requester: Any,
        ): BitPerfectTransport {
            if (!enabled) throw BitPerfectTransportException("off")
            if (owner != null && owner !== requester) throw BitPerfectTransportException("busy")
            val transport =
                if (driver == UsbBitPerfectDriver.DIRECT) {
                    val manager = usbDevices.manager ?: throw BitPerfectTransportException("no_usb")
                    val device = usbDevices.audioDevice() ?: throw BitPerfectTransportException("no_usb")
                    if (!usbDevices.hasPermission(device)) throw BitPerfectTransportException("permission_denied")
                    UsbDirectTransport.open(
                        manager = manager,
                        device = device,
                        sampleRate = sampleRate,
                        channels = channelCount,
                        sourceBits = sourceBits,
                        allowFixedVolume = allowFixedVolume,
                        volumeFraction = volumeFraction,
                    )
                } else {
                    if (!isMixerSupported) throw BitPerfectTransportException("android_version")
                    UsbBitPerfectTransport.open(audioManager, sampleRate, channelCount, sourceBits)
                }
            owner = requester
            activeTransport = transport
            return transport
        }

        @Synchronized
        internal fun release(requester: Any) {
            if (owner !== requester) return
            if (activeTransport is UsbDirectTransport) directReleasedAtMs = SystemClock.elapsedRealtime()
            owner = null
            activeTransport = null
            _status.update { current ->
                BitPerfectStatus(
                    dacName = current.dacName,
                    permissionRequired = current.permissionRequired,
                    fallback = current.fallback,
                )
            }
        }

        @Synchronized
        internal fun markFailed(
            sampleRate: Int,
            channelCount: Int,
            sourceBits: Int,
            reason: String,
        ) {
            failuresFor(currentDeviceId()).add(formatKey(sampleRate, channelCount, sourceBits))
            Timber.tag(TAG).i("Bit-perfect output unavailable (%s) for %d Hz/%d ch/%d-bit", reason, sampleRate, channelCount, sourceBits)
            val fallback =
                if (reason == UsbDirectTransport.REASON_VOLUME_UNAVAILABLE) {
                    BitPerfectFallback.NO_HARDWARE_VOLUME
                } else {
                    BitPerfectFallback.UNAVAILABLE
                }
            _status.update { current ->
                BitPerfectStatus(
                    dacName = current.dacName,
                    permissionRequired = current.permissionRequired,
                    fallback = fallback,
                )
            }
        }

        internal fun publishActive(
            transport: BitPerfectTransport,
            exact: Boolean,
        ) {
            _status.value =
                BitPerfectStatus(
                    dacName = transport.deviceName,
                    active = true,
                    exact = exact,
                    sampleRateHz = transport.sampleRate,
                    outputBits = transport.validBits,
                )
        }

        private fun onUsbDevicesChanged() {
            synchronized(this) { failedFormats.clear() }
            refreshDevice()
            _events.tryEmit(BitPerfectEvent.OUTPUT_CHANGED)
        }

        private fun onUsbDeviceDetached(deviceId: Int) {
            val streaming =
                synchronized(this) { (activeTransport as? UsbDirectTransport)?.takeIf { it.usbDeviceId == deviceId } }
            streaming?.markDetached()
            if (enabled && driver == UsbBitPerfectDriver.DIRECT) _events.tryEmit(BitPerfectEvent.DAC_DETACHED)
        }

        private fun currentDeviceId(): Int =
            if (driver == UsbBitPerfectDriver.DIRECT) {
                usbDevices.audioDevice()?.deviceId ?: NO_DEVICE
            } else {
                audioManager.usbOutputDevice()?.id ?: NO_DEVICE
            }

        private fun failuresFor(deviceId: Int): MutableSet<String> {
            if (failedDeviceId != deviceId) {
                failedFormats.clear()
                failedDeviceId = deviceId
            }
            return failedFormats
        }

        private fun formatKey(
            sampleRate: Int,
            channelCount: Int,
            sourceBits: Int,
        ) = "$sampleRate/$channelCount/$sourceBits"

        companion object {
            private const val TAG = "BitPerfectOutput"
            private const val NO_DEVICE = -1
            private const val DIRECT_HANDBACK_MS = 3000L
            private const val DEFAULT_VOLUME_FRACTION = 0.25f
            private const val DEFAULT_DEVICE_NAME = "USB"

            @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            val isMixerSupported: Boolean
                get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        }
    }
