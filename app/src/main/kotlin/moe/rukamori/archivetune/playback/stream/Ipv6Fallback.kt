/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Remembers that IPv6 does not reach Google video servers on the current network.
 *
 * YouTube binds a stream link to the IP address that requested it, so playback has to use the same
 * address family as the link. When a link made for IPv6 cannot connect, the only way out is a new
 * link made over IPv4. Once a connect failure is recorded for a network, [playbackNetworkConfiguration]
 * prefers IPv4 for that network, which also changes the stream cache key and forces a fresh link.
 * The flag is dropped as soon as the active network changes.
 */
internal object Ipv6Fallback {
    @Volatile
    private var failedNetworkHandle: Long? = null

    private var ipv4Base: Dns? = null
    private var ipv4Wrapped: Dns? = null

    fun isActive(networkHandle: Long?): Boolean {
        val failed = failedNetworkHandle
        return failed != null && failed == networkHandle
    }

    fun markFailure(networkHandle: Long?) {
        if (networkHandle != null) failedNetworkHandle = networkHandle
    }

    fun reset() {
        failedNetworkHandle = null
    }

    /**
     * Returns one stable wrapper per base resolver. The network configuration is compared with
     * equals, so a new wrapper on every call would look like a changed network each time.
     */
    @Synchronized
    fun ipv4First(base: Dns): Dns {
        val cached = ipv4Wrapped
        if (cached != null && ipv4Base === base) return cached
        return Ipv4FirstDns(base).also {
            ipv4Base = base
            ipv4Wrapped = it
        }
    }
}

/** Orders IPv4 addresses first and keeps IPv6 behind them as a fallback. */
internal class Ipv4FirstDns(
    private val delegate: Dns,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val (ipv4, other) = delegate.lookup(hostname).partition { it is Inet4Address }
        return ipv4 + other
    }
}

internal fun Throwable.isConnectFailure(): Boolean =
    generateSequence(this) { it.cause }.any {
        it is ConnectException ||
            it is NoRouteToHostException ||
            (it is SocketTimeoutException && it.message?.contains("connect", ignoreCase = true) == true)
    }

/**
 * Watches stream requests whose link was made for IPv6. A failed connect is recorded so the player's
 * normal retry builds a new link over IPv4. The IPv6 attempt gets a shorter connect timeout so the
 * person is not left waiting a full 30 seconds before the retry.
 */
internal class Ipv6ConnectFallbackInterceptor(
    private val activeNetworkHandle: () -> Long?,
    private val proxyInUse: Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val ipv6Link =
            !proxyInUse &&
                request.url.host.isGoogleVideoHost() &&
                request.url.queryParameter("ip")?.contains(':') == true
        if (!ipv6Link) return chain.proceed(request)
        try {
            return chain
                .withConnectTimeout(IPV6_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .proceed(request)
        } catch (failure: IOException) {
            if (failure.isConnectFailure()) {
                Ipv6Fallback.markFailure(activeNetworkHandle())
            }
            throw failure
        }
    }

    private companion object {
        const val IPV6_CONNECT_TIMEOUT_SECONDS = 10
    }
}
