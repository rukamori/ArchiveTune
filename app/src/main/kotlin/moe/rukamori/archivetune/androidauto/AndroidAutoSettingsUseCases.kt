/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.androidauto

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class AndroidAutoSettingsUseCases @Inject constructor(
    private val repository: AndroidAutoSettingsRepository,
) {
    val configuration: Flow<AndroidAutoConfiguration> = repository.configuration
    val networkState: Flow<AndroidAutoNetworkState> = repository.networkState

    fun observeSettings(): Flow<AndroidAutoSettingsSnapshot> = combine(
        repository.configuration,
        repository.connectionStatus,
    ) { configuration, connectionStatus ->
        AndroidAutoSettingsSnapshot(
            configuration = configuration,
            connectionStatus = connectionStatus,
            hasLocalAudioPermission = repository.hasLocalAudioPermission(),
        )
    }

    fun currentConfiguration(): AndroidAutoConfiguration = repository.currentConfiguration()
    fun hasLocalAudioPermission(): Boolean = repository.hasLocalAudioPermission()

    fun isOnlinePlaybackAllowed(configuration: AndroidAutoConfiguration): Boolean {
        val network = repository.currentNetworkState()
        return network.online && (configuration.meteredPlayback || !network.metered)
    }

    fun isRemoteArtworkAllowed(configuration: AndroidAutoConfiguration): Boolean {
        val network = repository.currentNetworkState()
        return network.online && (configuration.meteredArtwork || !network.metered)
    }

    suspend fun setOnlineRecommendations(enabled: Boolean) = repository.setOnlineRecommendations(enabled)
    suspend fun setOnlineVoiceSearch(enabled: Boolean) = repository.setOnlineVoiceSearch(enabled)
    suspend fun setLocalSongs(enabled: Boolean) = repository.setLocalSongs(enabled)
    suspend fun setMeteredPlayback(enabled: Boolean) = repository.setMeteredPlayback(enabled)
    suspend fun setMeteredArtwork(enabled: Boolean) = repository.setMeteredArtwork(enabled)

    suspend fun setAction(
        slot: AndroidAutoActionSlot,
        action: AndroidAutoCustomAction,
        current: AndroidAutoConfiguration,
    ) {
        require(slot == AndroidAutoActionSlot.SECONDARY || action != AndroidAutoCustomAction.NONE)
        val primary = when {
            slot == AndroidAutoActionSlot.PRIMARY -> action
            action != current.primaryAction -> current.primaryAction
            current.secondaryAction != AndroidAutoCustomAction.NONE -> current.secondaryAction
            action != AndroidAutoCustomAction.START_RADIO -> AndroidAutoCustomAction.START_RADIO
            else -> AndroidAutoCustomAction.LIKE
        }
        val secondary = when {
            slot == AndroidAutoActionSlot.SECONDARY -> action
            action == current.secondaryAction -> current.primaryAction
            else -> current.secondaryAction
        }
        require(primary != AndroidAutoCustomAction.NONE)
        repository.setActions(primary, secondary)
    }
}
