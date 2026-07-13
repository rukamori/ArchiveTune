package moe.rukamori.archivetune.tidal

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * DNS resolver that falls back to DNS-over-HTTPS (Cloudflare) when the system resolver can't resolve
 * a host. This is what lets TIDAL work on networks/ISPs that DNS-block `tidal.com` / `auth.tidal.com`
 * (e.g. regions where TIDAL isn't officially available): the system lookup fails with EAI_NODATA, so
 * we resolve the host over HTTPS instead, which the ISP can't block at the DNS layer.
 *
 * Endpoints are tried in order. The hostname endpoints (cloudflare-dns.com, dns.google) resolve via
 * the system DNS of a plain client, which works because ISPs that block `tidal.com` do not block the
 * DoH providers themselves. The IP-literal endpoint (`https://1.1.1.1/dns-query`) is kept as a last
 * resort for networks that block the DoH hostnames too — it needs no DNS of its own and Cloudflare's
 * TLS certificate includes the `1.1.1.1` IP SAN, so hostname verification still passes.
 */
internal object TidalDns : Dns {
    private val DOH_ENDPOINTS =
        listOf(
            "https://cloudflare-dns.com/dns-query",
            "https://dns.google/resolve",
            "https://1.1.1.1/dns-query",
        )

    // Plain client with the default system resolver, used only to reach the DoH endpoints (whose
    // hostnames are not blocked). Never routed through TidalDns to avoid infinite recursion.
    private val dohClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()

    override fun lookup(hostname: String): List<InetAddress> {
        // Prefer the system resolver; it's faster and respects local/VPN DNS when it works.
        val system =
            runCatching { Dns.SYSTEM.lookup(hostname) }.getOrDefault(emptyList())
        if (system.isNotEmpty()) return system

        // System DNS returned nothing (blocked / EAI_NODATA). Fall back to DoH.
        val viaDoh = resolveOverHttps(hostname)
        if (viaDoh.isNotEmpty()) {
            Timber.tag("TidalDns").d("resolved %s via DoH (%d address(es))", hostname, viaDoh.size)
            return viaDoh
        }
        throw UnknownHostException("Unable to resolve host \"$hostname\" via system DNS or DoH")
    }

    private fun resolveOverHttps(hostname: String): List<InetAddress> {
        // Query A (IPv4) first, then AAAA (IPv6) only if needed.
        val a = queryDoh(hostname, type = 1)
        val aaaa = if (a.isEmpty()) queryDoh(hostname, type = 28) else emptyList()
        val ips = a + aaaa
        return ips.mapNotNull { ip ->
            // getByName on an IP literal does not perform a DNS lookup, so this is safe/offline.
            runCatching { InetAddress.getByName(ip) }.getOrNull()
        }
    }

    private fun queryDoh(hostname: String, type: Int): List<String> {
        for (endpoint in DOH_ENDPOINTS) {
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
                }.getOrDefault(emptyList())
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }
}
