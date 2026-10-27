/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import moe.rukamori.archivetune.innertube.NetworkGatekeeper
import moe.rukamori.archivetune.innertube.YouTube
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

internal object ModuleNetworkPolicy {
    fun client(streaming: Boolean): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(NetworkGatekeeper)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = YouTube.dns.lookup(hostname)
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> = listOf(if (streaming) YouTube.streamOkHttpProxy else YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
        })
        .proxyAuthenticator { _, response ->
            val username = YouTube.proxyUsername
            val password = YouTube.proxyPassword
            if (username.isNullOrBlank() || password.isNullOrBlank() || response.request.header("Proxy-Authorization") != null) null
            else response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(username, password)).build()
        }.build()
}
