/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException

class Ipv6FallbackTest {
    @Before
    fun setUp() = Ipv6Fallback.reset()

    @After
    fun tearDown() = Ipv6Fallback.reset()

    @Test
    fun fallbackIsOnlyActiveForTheNetworkThatFailed() {
        assertFalse(Ipv6Fallback.isActive(7L))

        Ipv6Fallback.markFailure(7L)

        assertTrue(Ipv6Fallback.isActive(7L))
        assertFalse(Ipv6Fallback.isActive(8L))
        assertFalse(Ipv6Fallback.isActive(null))
    }

    @Test
    fun missingNetworkIsNeverRecorded() {
        Ipv6Fallback.markFailure(null)

        assertFalse(Ipv6Fallback.isActive(null))
    }

    @Test
    fun ipv4FirstKeepsOneStableWrapperPerResolver() {
        val first = Dns.SYSTEM
        val second = Dns { listOf(InetAddress.getByName("127.0.0.1")) }

        assertSame(Ipv6Fallback.ipv4First(first), Ipv6Fallback.ipv4First(first))
        assertTrue(Ipv6Fallback.ipv4First(first) !== Ipv6Fallback.ipv4First(second))
    }

    @Test
    fun ipv4FirstOrdersIpv4BeforeIpv6AndKeepsBoth() {
        val v6 = InetAddress.getByName("2a02:6680:1::e")
        val v4 = InetAddress.getByName("192.0.2.10")
        val dns = Ipv4FirstDns { listOf(v6, v4) }

        val result = dns.lookup("rr3---sn-test.googlevideo.com")

        assertEquals(listOf(v4, v6), result)
        assertTrue(result.first() is Inet4Address)
        assertTrue(result.last() is Inet6Address)
    }

    @Test
    fun connectFailuresAreRecognisedThroughWrappedCauses() {
        val timeout = SocketTimeoutException("failed to connect to host (port 443) after 30000ms")
        val wrapped = IOException("outer", IOException("middle", timeout))

        assertTrue(wrapped.isConnectFailure())
        assertTrue(ConnectException("refused").isConnectFailure())
    }

    @Test
    fun readTimeoutsAndOtherErrorsAreNotConnectFailures() {
        assertFalse(SocketTimeoutException("Read timed out").isConnectFailure())
        assertFalse(IOException("unexpected end of stream").isConnectFailure())
    }

    @Test
    fun ipv6StreamLinkConnectFailureRecordsTheNetwork() {
        val error = runCatching { newCall(link(ip = "2a02:6680:1424:2a0a::1")) }.exceptionOrNull()

        assertTrue(error is SocketTimeoutException)
        assertTrue(Ipv6Fallback.isActive(42L))
    }

    @Test
    fun ipv4StreamLinkFailureIsLeftAlone() {
        runCatching { newCall(link(ip = "203.0.113.5")) }

        assertFalse(Ipv6Fallback.isActive(42L))
    }

    @Test
    fun otherHostsAreLeftAlone() {
        runCatching { newCall("https://example.com/videoplayback?ip=2a02:6680:1424:2a0a::1") }

        assertFalse(Ipv6Fallback.isActive(42L))
    }

    @Test
    fun proxiedRequestsAreLeftAlone() {
        runCatching { newCall(link(ip = "2a02:6680:1424:2a0a::1"), proxyInUse = true) }

        assertFalse(Ipv6Fallback.isActive(42L))
    }

    @Test
    fun nonConnectFailuresOnIpv6LinksAreLeftAlone() {
        runCatching {
            newCall(
                link(ip = "2a02:6680:1424:2a0a::1"),
                failure = SocketTimeoutException("Read timed out"),
            )
        }

        assertFalse(Ipv6Fallback.isActive(42L))
    }

    private fun link(ip: String) = "https://rr3---sn-test.googlevideo.com/videoplayback?ip=$ip"

    /**
     * Runs a request through the interceptor with a terminal interceptor that always fails, so no
     * network access is needed.
     */
    private fun newCall(
        url: String,
        proxyInUse: Boolean = false,
        failure: IOException =
            SocketTimeoutException("failed to connect to host (port 443) after 10000ms"),
    ) {
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor(Ipv6ConnectFallbackInterceptor({ 42L }, proxyInUse))
                .addInterceptor(Interceptor { throw failure })
                .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
    }
}
