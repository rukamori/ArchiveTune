/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.androidauto

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.car.app.connection.CarConnection
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Observer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.AndroidAutoLocalSongsKey
import moe.rukamori.archivetune.constants.AndroidAutoMeteredArtworkKey
import moe.rukamori.archivetune.constants.AndroidAutoMeteredPlaybackKey
import moe.rukamori.archivetune.constants.AndroidAutoOnlineRecommendationsKey
import moe.rukamori.archivetune.constants.AndroidAutoOnlineVoiceSearchKey
import moe.rukamori.archivetune.constants.AndroidAutoPrimaryActionKey
import moe.rukamori.archivetune.constants.AndroidAutoSecondaryActionKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAutoSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    val configuration: Flow<AndroidAutoConfiguration> = context.dataStore.data.map { preferences ->
        AndroidAutoConfiguration(
            onlineRecommendations = preferences[AndroidAutoOnlineRecommendationsKey] ?: true,
            onlineVoiceSearch = preferences[AndroidAutoOnlineVoiceSearchKey] ?: true,
            localSongs = preferences[AndroidAutoLocalSongsKey] ?: true,
            meteredPlayback = preferences[AndroidAutoMeteredPlaybackKey] ?: true,
            meteredArtwork = preferences[AndroidAutoMeteredArtworkKey] ?: true,
            primaryAction = AndroidAutoCustomAction.fromPreference(
                preferences[AndroidAutoPrimaryActionKey],
                AndroidAutoCustomAction.LIKE,
            ),
            secondaryAction = AndroidAutoCustomAction.fromPreference(
                preferences[AndroidAutoSecondaryActionKey],
                AndroidAutoCustomAction.START_RADIO,
            ),
        )
    }.distinctUntilChanged()

    val connectionStatus: Flow<AndroidAutoConnectionStatus> = callbackFlow {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            trySend(AndroidAutoConnectionStatus.NATIVE)
            close()
            return@callbackFlow
        }
        val connection = CarConnection(context)
        val observer = Observer<Int> { type -> trySend(type.toConnectionStatus()) }
        connection.type.observeForever(observer)
        awaitClose { connection.type.removeObserver(observer) }
    }.distinctUntilChanged()

    val networkState: Flow<AndroidAutoNetworkState> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(AndroidAutoNetworkState())
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentNetworkState())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.toAndroidAutoNetworkState())
            }

            override fun onLost(network: Network) {
                trySend(currentNetworkState())
            }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(currentNetworkState())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun currentConfiguration(): AndroidAutoConfiguration = AndroidAutoConfiguration(
        onlineRecommendations = context.dataStore.get(AndroidAutoOnlineRecommendationsKey, true),
        onlineVoiceSearch = context.dataStore.get(AndroidAutoOnlineVoiceSearchKey, true),
        localSongs = context.dataStore.get(AndroidAutoLocalSongsKey, true),
        meteredPlayback = context.dataStore.get(AndroidAutoMeteredPlaybackKey, true),
        meteredArtwork = context.dataStore.get(AndroidAutoMeteredArtworkKey, true),
        primaryAction = AndroidAutoCustomAction.fromPreference(
            context.dataStore[AndroidAutoPrimaryActionKey],
            AndroidAutoCustomAction.LIKE,
        ),
        secondaryAction = AndroidAutoCustomAction.fromPreference(
            context.dataStore[AndroidAutoSecondaryActionKey],
            AndroidAutoCustomAction.START_RADIO,
        ),
    )

    fun currentNetworkState(): AndroidAutoNetworkState {
        val manager = connectivityManager ?: return AndroidAutoNetworkState()
        val network = manager.activeNetwork ?: return AndroidAutoNetworkState()
        val capabilities = manager.getNetworkCapabilities(network) ?: return AndroidAutoNetworkState()
        return capabilities.toAndroidAutoNetworkState()
    }

    fun hasLocalAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        },
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun setOnlineRecommendations(enabled: Boolean) = edit(AndroidAutoOnlineRecommendationsKey, enabled)
    suspend fun setOnlineVoiceSearch(enabled: Boolean) = edit(AndroidAutoOnlineVoiceSearchKey, enabled)
    suspend fun setLocalSongs(enabled: Boolean) = edit(AndroidAutoLocalSongsKey, enabled)
    suspend fun setMeteredPlayback(enabled: Boolean) = edit(AndroidAutoMeteredPlaybackKey, enabled)
    suspend fun setMeteredArtwork(enabled: Boolean) = edit(AndroidAutoMeteredArtworkKey, enabled)

    suspend fun setActions(primary: AndroidAutoCustomAction, secondary: AndroidAutoCustomAction) {
        context.dataStore.edit { preferences ->
            preferences[AndroidAutoPrimaryActionKey] = primary.name
            preferences[AndroidAutoSecondaryActionKey] = secondary.name
        }
    }

    private suspend fun <T> edit(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private fun Int.toConnectionStatus(): AndroidAutoConnectionStatus = when (this) {
        CarConnection.CONNECTION_TYPE_PROJECTION -> AndroidAutoConnectionStatus.PROJECTION
        CarConnection.CONNECTION_TYPE_NATIVE -> AndroidAutoConnectionStatus.NATIVE
        else -> AndroidAutoConnectionStatus.DISCONNECTED
    }
}

private fun NetworkCapabilities.toAndroidAutoNetworkState(): AndroidAutoNetworkState = AndroidAutoNetworkState(
    online = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
)
