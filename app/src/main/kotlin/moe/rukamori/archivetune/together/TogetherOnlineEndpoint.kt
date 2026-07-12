/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import moe.rukamori.archivetune.constants.TogetherOnlineEndpointCacheKey
import moe.rukamori.archivetune.constants.TogetherOnlineEndpointLastCheckedAtKey
import moe.rukamori.archivetune.utils.getAsync
import java.net.URI
import java.util.concurrent.TimeUnit

object TogetherOnlineEndpoint {
    private const val EndpointSourceUrl =
        "https://raw.githubusercontent.com/rukamori/ArchiveTune/refs/heads/dev/ArchiveTuneKoiverseServer.txt"

    private const val CacheTtlMs: Long = 6 * 60 * 60 * 1000L

    private val httpClient =
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(12, TimeUnit.SECONDS)
                    readTimeout(12, TimeUnit.SECONDS)
                    writeTimeout(12, TimeUnit.SECONDS)
                    retryOnConnectionFailure(true)
                }
            }
        }

    suspend fun baseUrlOrNull(dataStore: DataStore<Preferences>): String? {
        val now = System.currentTimeMillis()
        val cached = dataStore.getAsync(TogetherOnlineEndpointCacheKey)?.trim().orEmpty()
        val lastCheckedAt = dataStore.getAsync(TogetherOnlineEndpointLastCheckedAtKey, 0L)

        if (cached.isNotBlank() && now - lastCheckedAt < CacheTtlMs) return cached

        val fetched = fetchEndpointFromSourceOrNull()
        if (fetched != null) {
            dataStore.edit { prefs ->
                prefs[TogetherOnlineEndpointCacheKey] = fetched
                prefs[TogetherOnlineEndpointLastCheckedAtKey] = now
            }
            return fetched
        }

        if (cached.isNotBlank()) {
            dataStore.edit { prefs ->
                prefs[TogetherOnlineEndpointLastCheckedAtKey] = now
            }
            return cached
        }

        dataStore.edit { prefs ->
            prefs[TogetherOnlineEndpointLastCheckedAtKey] = now
        }
        return null
    }

    private suspend fun fetchEndpointFromSourceOrNull(): String? {
        val text =
            runCatching { httpClient.get(EndpointSourceUrl).bodyAsText() }
                .getOrNull()
                ?.trim()
                .orEmpty()
        if (text.isBlank()) return null

        val candidate =
            text
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?: return null

        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.trim()?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.trim().orEmpty()
        if (host.isBlank()) return null

        return candidate.trimEnd('/')
    }

    fun onlineWebSocketUrlOrNull(
        rawWsUrl: String,
        baseUrl: String,
    ): String? {
        val derived = deriveWebSocketUrlFromBaseUrl(baseUrl) ?: return null
        val normalized = normalizeWebSocketUrl(rawWsUrl, baseUrl) ?: return derived

        val host =
            runCatching { URI(normalized).host }.getOrNull()?.trim()?.lowercase()
                ?: return derived
        if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0") return derived

        val baseHost =
            runCatching { URI(baseUrl.trim()).host }.getOrNull()?.trim()?.lowercase()
        if (baseHost != null && isIpv4Address(baseHost) && !isIpv4Address(host)) return derived

        return normalized
    }

    private fun isIpv4Address(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull() ?: return@all false
            n in 0..255 && part == n.toString()
        }
    }

    private fun deriveWebSocketUrlFromBaseUrl(baseUrl: String): String? {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return null
        val host = uri.host?.trim()?.ifBlank { null } ?: return null
        val scheme = uri.scheme?.trim()?.lowercase()
        val wsScheme = if (scheme == "https") "wss" else "ws"

        val portPart = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
        val normalizedPath =
            uri.path
                ?.trim()
                ?.trimEnd('/')
                .orEmpty()
                .let { if (it.endsWith("/v1")) it else "$it/v1" }

        return "$wsScheme://$host$portPart$normalizedPath/together/ws"
    }

    private fun normalizeWebSocketUrl(
        raw: String,
        baseUrl: String,
    ): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) return trimmed
        if (trimmed.startsWith("http://")) return "ws://${trimmed.removePrefix("http://")}"
        if (trimmed.startsWith("https://")) return "wss://${trimmed.removePrefix("https://")}"
        if (trimmed.startsWith("/")) {
            val baseUri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return null
            val host = baseUri.host?.trim()?.ifBlank { null } ?: return null
            val scheme = baseUri.scheme?.trim()?.lowercase()
            val wsScheme = if (scheme == "https") "wss" else "ws"
            val portPart = if (baseUri.port != -1 && baseUri.port != 80 && baseUri.port != 443) ":${baseUri.port}" else ""
            val basePath =
                baseUri.path
                    ?.trim()
                    ?.trimEnd('/')
                    .orEmpty()
            return "$wsScheme://$host$portPart$basePath$trimmed"
        }

        val baseScheme = runCatching { URI(baseUrl.trim()).scheme?.trim()?.lowercase() }.getOrNull()
        val wsScheme = if (baseScheme == "https") "wss" else "ws"
        return "$wsScheme://$trimmed"
    }
}
