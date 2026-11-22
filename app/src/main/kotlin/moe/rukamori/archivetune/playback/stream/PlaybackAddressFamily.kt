/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Protocol
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

internal val playbackAddressFamilyFallback = PlaybackAddressFamilyFallback()

internal class PlaybackAddressFamilyFallback {
    private val ipv4NetworkIdentity = AtomicReference<String?>(null)

    fun dns(
        networkIdentity: String,
        delegate: Dns,
    ): Dns = if (networkIdentity == ipv4NetworkIdentity.get()) Ipv4PreferredDns(delegate) else delegate

    fun connectionListener(
        networkIdentity: String,
        onFallback: () -> Unit,
    ): EventListener =
        object : EventListener() {
            override fun connectFailed(
                call: Call,
                inetSocketAddress: InetSocketAddress,
                proxy: Proxy,
                protocol: Protocol?,
                ioe: IOException,
            ) {
                if (proxy.type() != Proxy.Type.DIRECT ||
                    inetSocketAddress.address !is Inet6Address ||
                    !ioe.isUnreachableConnect() ||
                    call.isCanceled() ||
                    !call.request().url.host.isGoogleVideoHost()
                ) {
                    return
                }
                if (ipv4NetworkIdentity.getAndSet(networkIdentity) != networkIdentity) onFallback()
            }
        }
}

private data class Ipv4PreferredDns(
    private val delegate: Dns,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        return addresses.filterIsInstance<Inet4Address>().ifEmpty { addresses }
    }
}

private fun IOException.isUnreachableConnect(): Boolean =
    this is SocketTimeoutException || this is ConnectException || this is NoRouteToHostException

internal fun String.isGoogleVideoHost(): Boolean = this == "googlevideo.com" || endsWith(".googlevideo.com")
