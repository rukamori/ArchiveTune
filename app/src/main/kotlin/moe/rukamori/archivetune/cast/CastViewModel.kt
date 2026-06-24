/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.cast

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CastViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = CastPlaybackRepositoryLocator.get(application)
    private val observeCastStateUseCase = ObserveCastStateUseCase(repository)
    private val disconnectCastSessionUseCase = DisconnectCastSessionUseCase(repository)
    private val setCastVolumeUseCase = SetCastVolumeUseCase(repository)
    private val _isRoutePickerVisible = MutableStateFlow(false)

    val screenState = observeCastStateUseCase()
    val isRoutePickerVisible: StateFlow<Boolean> = _isRoutePickerVisible.asStateFlow()

    fun disconnect() = disconnectCastSessionUseCase()

    fun setVolume(volume: Float) = setCastVolumeUseCase(volume)

    fun showRoutePicker() {
        _isRoutePickerVisible.value = true
    }

    fun hideRoutePicker() {
        _isRoutePickerVisible.value = false
    }
}
