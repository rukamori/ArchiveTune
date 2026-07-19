/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerPlaybackController
    @Inject
    constructor() {
        private val _capabilities = MutableStateFlow<EqCapabilities?>(null)
        val capabilities = _capabilities.asStateFlow()

        private var serviceReference = WeakReference<MusicService>(null)

        internal fun attach(service: MusicService) {
            serviceReference = WeakReference(service)
            _capabilities.value = service.eqCapabilities.value
        }

        internal fun detach(service: MusicService) {
            if (serviceReference.get() === service) {
                serviceReference.clear()
                _capabilities.value = null
            }
        }

        internal fun updateCapabilities(capabilities: EqCapabilities?) {
            _capabilities.value = capabilities
        }

        fun applyFlatPreset(): Boolean {
            val service = serviceReference.get() ?: return false
            service.applyEqFlatPreset()
            return true
        }

        fun applySystemPreset(index: Int): Boolean {
            val service = serviceReference.get() ?: return false
            service.applySystemEqPreset(index)
            return true
        }
    }
