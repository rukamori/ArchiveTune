/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import moe.rukamori.archivetune.constants.LowDataModeKey

fun Context.isLowDataModeActive(): Boolean = isLowDataModeActive(dataStore.get(LowDataModeKey, false))

fun Context.isLowDataModeActive(enabled: Boolean): Boolean {
    if (!enabled) return false
    val connectivityManager = getSystemService<ConnectivityManager>() ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
    return connectivityManager.isActiveNetworkMetered ||
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
}

@Composable
fun rememberLowDataModeActive(): Boolean {
    val enabled by rememberPreference(LowDataModeKey, false)
    return rememberLowDataModeActive(enabled)
}

@Composable
fun rememberLowDataModeActive(enabled: Boolean): Boolean {
    val context = LocalContext.current
    var active by remember(context, enabled) {
        mutableStateOf(context.isLowDataModeActive(enabled))
    }

    DisposableEffect(context, enabled) {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        if (connectivityManager == null || !enabled) {
            active = false
            return@DisposableEffect onDispose {}
        }

        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    active = context.isLowDataModeActive(enabled)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    active = context.isLowDataModeActive(enabled)
                }

                override fun onLost(network: Network) {
                    active = context.isLowDataModeActive(enabled)
                }
            }

        active = context.isLowDataModeActive(enabled)
        val request =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

        runCatching {
            connectivityManager.registerNetworkCallback(request, callback)
        }.onFailure {
            active = context.isLowDataModeActive(enabled)
        }

        onDispose {
            runCatching {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
    }

    return active
}
