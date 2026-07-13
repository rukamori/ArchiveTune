package moe.rukamori.archivetune.tidal

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS resolver that falls back to DNS-over-HTTPS (Cloudflare / Google) when the system resolver
 * can't resolve a host. This is what lets TIDAL work on networks/ISPs that DNS-block `tidal.com` /
 * `auth.tidal.com` (e.g. regions where TIDAL isn't officially available): the system lookup fails
 * with EAI_NODATA, so we resolve the host over HTTPS instead, which the ISP can't block at the DNS
 * layer.
 *
 * Robustness details (why this differs from a naive DoH resolver):
 *  - The DoH client is **bootstrapped with hardcoded IPs** for the DoH provider hostnames
 *    (cloudflare-dns.com, dns.google). A naive resolver still needs the *system* DNS to resolve the
 *    DoH provider's own hostname first — so on a network where system DNS is fully broken (not just
 *    tidal-blocked), even the fallback fails with "Unable to resolve host". Pinning the provider IPs
 *    removes that dependency entirely; TLS still uses the hostname for SNI + certificate validation,
 *    so it stays secure. (Note: querying the JSON DoH API by raw IP literal, e.g.
 *    `https://1.1.1.1/dns-query`, does not reliably return JSON, which is why we pin the hostname
 *    instead of using IP-literal URLs.)
 *  - Successful resolutions are **cached** (with a TTL) so repeated lookups during playback don't
 *    hammer the DoH endpoints and survive brief connectivity blips.
 */
internal object TidalDns : Dns {
    // DoH providers that support the JSON API (application/dns-json). Both are reached via the
    // bootstrap resolver below, so they never depend on the system DNS.
    private const val CLOUDFLARE_DOH = "https://cloudflare-dns.com/dns-query"
    private const val GOOGLE_DOH = "https://dns.google/resolve"

    // Hardcoded provider IPs so we can reach the DoH endpoints without any working system DNS.
    // These are stable anycast addresses for the respective providers.
    private val BOOTSTRAP: Map<String, List<String>> =
        mapOf(
            "cloudflare-dns.com" to listOf("104.16.248.249", "104.16.249.249"),
            "dns.google" to listOf("8.8.8.8", "8.8.4.4"),
        )

    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    private class CacheEntry(val addresses: List<InetAddress>, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** Static bootstrap resolver used ONLY to reach the DoH provider hostnames. */
    private val bootstrapDns =
        Dns { hostname ->
            BOOTSTRAP[hostname]?.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }
                ?: Dns.SYSTEM.lookup(hostname)
        }

    // Plain client for the DoH endpoints. Uses the bootstrap resolver (never TidalDns) to avoid
    // both infinite recursion and any dependency on the system DNS.
    private val dohClient =
        OkHttpClient
            .Builder()
            .dns(bootstrapDns)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

    override fun lookup(hostname: String): List<InetAddress> {
        // Serve from cache first (covers brief network drops during playback).
        cache[hostname]?.let { entry ->
            if (entry.expiresAt > System.currentTimeMillis() && entry.addresses.isNotEmpty()) {
                return entry.addresses
            }
        }

        // Prefer the system resolver; it's faster and respects local/VPN DNS when it works.
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        if (system.isNotEmpty()) {
            cache[hostname] = CacheEntry(system, System.currentTimeMillis() + CACHE_TTL_MS)
            return system
        }

        // System DNS returned nothing (blocked / EAI_NODATA). Fall back to DoH.
        val viaDoh = resolveOverHttps(hostname)
        if (viaDoh.isNotEmpty()) {
            Timber.tag("TidalDns").d("resolved %s via DoH (%d address(es))", hostname, viaDoh.size)
            cache[hostname] = CacheEntry(viaDoh, System.currentTimeMillis() + CACHE_TTL_MS)
            return viaDoh
        }

        // Last resort: if DoH also failed but we have a stale cache entry, use it rather than crash.
        cache[hostname]?.addresses?.takeIf { it.isNotEmpty() }?.let {
            Timber.tag("TidalDns").w("using stale cached DNS for %s (DoH failed)", hostname)
            return it
        }
        throw UnknownHostException("Unable to resolve host \"$hostname\" via system DNS or DoH")
    }

    private fun resolveOverHttps(hostname: String): List<InetAddress> {
        // Query A (IPv4) first, then AAAA (IPv6) only if needed.
        val a = queryDoh(hostname, type = 1)
        val aaaa = if (a.isEmpty()) queryDoh(hostname, type = 28) else emptyList()
        return (a + aaaa).mapNotNull { ip ->
            // getByName on an IP literal does not perform a DNS lookup, so this is safe/offline.
            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }
    }

    private fun queryDoh(hostname: String, type: Int): List<String> {
        for (endpoint in listOf(CLOUDFLARE_DOH, GOOGLE_DOH)) {
            val result =
                runCatching {
                    val request =
                        Request
                            .Builder()
                            .url("$endpoint?name=$hostname&type=$type")
                            .header("Accept", "application/dns-json")
                            .get()
                            .build()
                    dohClient.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        if (!response.isSuccessful || body.isBlank()) return@use emptyList()
                        val answers = JSONObject(body).optJSONArray("Answer") ?: return@use emptyList()
                        buildList {
                            for (i in 0 until answers.length()) {
                                val entry = answers.optJSONObject(i) ?: continue
                                // Only take A (1) / AAAA (28) records; skip CNAME (5) etc.
                                if (entry.optInt("type") != type) continue
                                entry.optString("data").takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }
                }.getOrElse { emptyList() }
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }
}
