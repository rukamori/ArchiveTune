/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import android.content.Context
import android.net.ConnectivityManager
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiNetworkConfiguration
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet6Address
import java.net.Proxy

internal fun playbackNetworkConfiguration(context: Context): YoutubeiNetworkConfiguration {
    val manager = requireNotNull(context.getSystemService(ConnectivityManager::class.java))
    val network = manager.activeNetwork
    return YoutubeiNetworkConfiguration(
        proxy = YouTube.streamProxy,
        proxyUsername = YouTube.proxyUsername,
        proxyPassword = YouTube.proxyPassword,
        dns = YouTube.dns,
        networkIdentity = "${network?.networkHandle}:${network?.let(manager::getLinkProperties)}",
    )
}

internal class PlaybackHttpClient(
    private val context: Context,
    private val baseClient: OkHttpClient,
    private val connectionPool: () -> ConnectionPool = { ConnectionPool() },
) : Call.Factory {
    private var configuration: YoutubeiNetworkConfiguration? = null
    private var client: OkHttpClient? = null
    private var streamIpv6: Boolean? = null

    @Synchronized
    override fun newCall(request: Request): Call {
        val current = playbackNetworkConfiguration(context)
        val requestedIpv6 = request.url.queryParameter("ip")
            ?.takeIf { it.isNotBlank() && request.url.host.isGoogleVideoHost() && current.proxy == null }
            ?.contains(':')
        if (current != configuration || requestedIpv6 != streamIpv6) {
            client?.connectionPool?.evictAll()
            val username = current.proxyUsername
            val password = current.proxyPassword
            val dns = if (requestedIpv6 == null) current.dns else Dns { hostname ->
                val addresses = current.dns.lookup(hostname)
                if (hostname.isGoogleVideoHost()) {
                    addresses.filter { (it is Inet6Address) == requestedIpv6 }
                        .ifEmpty { addresses }
                } else {
                    addresses
                }
            }
            client = baseClient.newBuilder()
                .connectionPool(connectionPool())
                .proxy(current.proxy ?: Proxy.NO_PROXY)
                .dns(dns)
                .proxyAuthenticator { _, response ->
                    if (current.proxy == null || username.isNullOrBlank() || password.isNullOrBlank() ||
                        response.request.header("Proxy-Authorization") != null
                    ) {
                        null
                    } else {
                        response.request.newBuilder()
                            .header("Proxy-Authorization", Credentials.basic(username, password))
                            .build()
                    }
                }
                .build()
            configuration = current
            streamIpv6 = requestedIpv6
        }
        return requireNotNull(client).newCall(request)
    }
}

private fun String.isGoogleVideoHost(): Boolean = this == "googlevideo.com" || endsWith(".googlevideo.com")
