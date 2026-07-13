/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 *
 * Amazon Music source via a public instance (e.g. the Monochrome community's `amz.*` host).
 *
 * The instance itself is ASIN-based and exposes NO search endpoint, so title -> ASIN mapping is done
 * externally via two public, no-auth hops (iTunes Search -> Odesli/song.link), see [searchAsin].
 *
 * IMPORTANT — streaming is still gated behind Cloudflare Turnstile. Even with a valid ASIN, the
 * instance requires either a configured `bypass_token` (set by the instance operator) or a
 * Turnstile JWT obtained interactively in a browser. Without one, resolution finds the ASIN but the
 * stream request is rejected (HTTP 428) and the caller falls through to the next configured source.
 */

package moe.rukamori.archivetune.audiosource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.AudioSourceType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object AmazonAudioProvider {
    const val DEFAULT_INSTANCE = "https://amz.geeked.wtf"

    // Public, no-auth catalog used to look up a track (title/artist -> Apple Music track URL).
    private const val ITUNES_SEARCH = "https://itunes.apple.com/search"

    // Odesli/song.link: given a known track URL it returns cross-platform IDs, including the Amazon
    // Music song entity whose id IS the streaming ASIN. Free + public (rate limited).
    private const val ODESLI_LINKS = "https://api.song.link/v1-alpha.1/links"

    // Cloudflare Turnstile config used by the official monochrome.tf web player. The sitekey is
    // domain-locked to monochrome.tf, so the challenge must be solved from that origin (see
    // AmazonTurnstileActivity, which renders the widget via loadDataWithBaseURL on that host).
    const val TURNSTILE_SITEKEY = "0x4AAAAAADgxqF6QVMm0GLHH"
    const val TURNSTILE_ORIGIN = "https://monochrome.tf/"

    // Turnstile JWTs mint with a ~1h lifetime; we treat them as valid for a little less to leave a
    // safety margin for clock skew and in-flight requests.
    const val TURNSTILE_JWT_TTL_MS = 60L * 60L * 1000L
    const val TURNSTILE_JWT_SAFETY_MS = 5L * 60L * 1000L

    private val client =
        OkHttpClient
            .Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    /**
     * Exchanges a solved Cloudflare Turnstile response token for the instance's access JWT, exactly
     * as the web player does: `POST {instance}/api/auth/turnstile` with `{cf_turnstile_response}`,
     * returning the `access_token` field. The returned JWT is sent as the `X-Turnstile-JWT` header
     * on subsequent track/stream requests. Returns null on failure.
     */
    suspend fun exchangeTurnstileToken(
        cfTurnstileResponse: String,
        instanceBaseUrl: String = DEFAULT_INSTANCE,
    ): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    instanceBaseUrl.trimEnd('/').toHttpUrl()
                        .newBuilder()
                        .addPathSegment("api")
                        .addPathSegment("auth")
                        .addPathSegment("turnstile")
                        .build()
                val bodyJson = JSONObject().put("cf_turnstile_response", cfTurnstileResponse).toString()
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                        .post(bodyJson.toRequestBody("application/json".toMediaType()))
                        .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful || body.isBlank()) {
                        Timber.tag("AmazonAudio").w("Turnstile exchange failed: HTTP %d", response.code)
                        return@use null
                    }
                    JSONObject(body).optString("access_token").ifBlank { null }
                }
            }.getOrElse {
                Timber.tag("AmazonAudio").w(it, "Turnstile exchange error")
                null
            }
        }

    // Small in-memory cache of "title|artist" -> ASIN (or "" for a known miss) to avoid hammering
    // the public iTunes/Odesli endpoints for repeat plays within a session.
    private val asinCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Attempts to resolve a stream from track metadata. Since the Amazon instance is ASIN-only, we
     * first map the title/artist to an Amazon Music ASIN via [searchAsin] (iTunes + Odesli), then
     * hand the ASIN to [resolveByAsin]. Returns null if no ASIN can be found or the instance
     * rejects the stream (e.g. Turnstile), so the caller falls through to the next source.
     */
    suspend fun resolveByMetadata(
        title: String,
        artists: List<String>,
        durationMs: Long?,
        instanceBaseUrl: String = DEFAULT_INSTANCE,
        bypassToken: String? = null,
        quality: String = "HIGH",
        turnstileJwt: String? = null,
    ): DirectStream? {
        val asin = searchAsin(title, artists, durationMs)
        if (asin == null) {
            Timber.tag("AmazonAudio").d("No Amazon ASIN found for \"%s\"; skipping.", title)
            return null
        }
        Timber.tag("AmazonAudio").d("Mapped \"%s\" -> ASIN %s; resolving stream.", title, asin)
        return resolveByAsin(
            asin = asin,
            quality = quality,
            instanceBaseUrl = instanceBaseUrl,
            bypassToken = bypassToken,
            turnstileJwt = turnstileJwt,
        )
    }

    /**
     * Maps a track title/artist to an Amazon Music streaming ASIN using two public, no-auth hops:
     *   1. iTunes Search API -> best-matching Apple Music track URL (disambiguated by title/artist
     *      and, when available, duration).
     *   2. Odesli (song.link) -> the `AMAZON_SONG::<ASIN>` entity for that track.
     * Results (including misses) are cached in-memory for the session. Returns null on no match.
     */
    suspend fun searchAsin(
        title: String,
        artists: List<String>,
        durationMs: Long?,
    ): String? =
        withContext(Dispatchers.IO) {
            val primaryArtist = artists.firstOrNull().orEmpty()
            val cacheKey = "${title.trim().lowercase()}|${primaryArtist.trim().lowercase()}"
            asinCache[cacheKey]?.let { return@withContext it.ifBlank { null } }

            val result =
                runCatching {
                    val trackUrl = itunesLookupTrackUrl(title, primaryArtist, durationMs)
                    if (trackUrl == null) {
                        Timber.tag("AmazonAudio").d("iTunes had no match for \"%s\" - %s", title, primaryArtist)
                        return@runCatching null
                    }
                    odesliAmazonAsin(trackUrl)
                }.getOrElse {
                    Timber.tag("AmazonAudio").w(it, "ASIN search error for \"%s\"", title)
                    null
                }
            // Cache both hits and misses ("" == known miss) to protect the public endpoints.
            asinCache[cacheKey] = result.orEmpty()
            result
        }

    /** iTunes Search: returns the best-matching Apple Music track URL, or null. */
    private fun itunesLookupTrackUrl(
        title: String,
        artist: String,
        durationMs: Long?,
    ): String? {
        val term = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val url =
            ITUNES_SEARCH
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("term", term)
                .addQueryParameter("entity", "song")
                .addQueryParameter("limit", "10")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) return null
            val results = JSONObject(body).optJSONArray("results") ?: return null
            if (results.length() == 0) return null

            val normTitle = normalize(title)
            val normArtist = normalize(artist)
            var bestUrl: String? = null
            var bestScore = Int.MIN_VALUE
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val trackName = item.optString("trackName")
                val artistName = item.optString("artistName")
                val viewUrl = item.optString("trackViewUrl").ifBlank { null } ?: continue
                var score = 0
                val nt = normalize(trackName)
                if (nt == normTitle) score += 100 else if (nt.contains(normTitle) || normTitle.contains(nt)) score += 50
                if (normArtist.isNotEmpty() && normalize(artistName).contains(normArtist)) score += 60
                // Prefer close duration matches when we know the expected duration.
                if (durationMs != null && durationMs > 0) {
                    val itMs = item.optLong("trackTimeMillis", 0L)
                    if (itMs > 0 && abs(itMs - durationMs) <= 3000) score += 40
                }
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = viewUrl
                }
            }
            // Require at least a title hit to avoid returning an unrelated track.
            return if (bestScore >= 50) bestUrl else null
        }
    }

    /** Odesli: extracts the Amazon Music ASIN for a known track URL, or null. */
    private fun odesliAmazonAsin(trackUrl: String): String? {
        val url =
            ODESLI_LINKS
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("url", trackUrl)
                .addQueryParameter("userCountry", "US")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) return null
            val entities = JSONObject(body).optJSONObject("entitiesByUniqueId") ?: return null
            val keys = entities.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.startsWith("AMAZON_SONG::", ignoreCase = true)) {
                    // Key format is AMAZON_SONG::<ASIN>; also fall back to the entity's id field.
                    val fromKey = key.substringAfter("::").trim()
                    if (fromKey.isNotBlank()) return fromKey
                    return entities.optJSONObject(key)?.optString("id")?.ifBlank { null }
                }
            }
            return null
        }
    }

    /** Lowercases and strips punctuation/parenthetical noise for fuzzy title/artist comparison. */
    private fun normalize(value: String): String =
        value
            .lowercase()
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /**
     * Resolves a decrypted stream for a known Amazon ASIN. The instance's `/decrypt` endpoint
     * returns already-decrypted audio bytes (server-side Widevine), so the returned URI is directly
     * playable. Returns null if the instance rejects the request (e.g. Turnstile challenge / 428).
     */
    suspend fun resolveByAsin(
        asin: String,
        quality: String = "HIGH",
        instanceBaseUrl: String = DEFAULT_INSTANCE,
        bypassToken: String? = null,
        turnstileJwt: String? = null,
    ): DirectStream? =
        withContext(Dispatchers.IO) {
            val base = instanceBaseUrl.trim().trimEnd('/').ifBlank { DEFAULT_INSTANCE }
            val streamUrl =
                buildString {
                    append("$base/api/stream/$asin/decrypt?quality=$quality")
                    if (!bypassToken.isNullOrBlank()) append("&bypass_token=$bypassToken")
                }
            // Probe with a HEAD-like GET to detect a Turnstile/challenge before handing the URL to
            // the player. If the probe is rejected we skip Amazon for this track.
            val probe =
                Request
                    .Builder()
                    .url(streamUrl)
                    .apply { if (!turnstileJwt.isNullOrBlank()) header("X-Turnstile-JWT", turnstileJwt) }
                    .header("User-Agent", "Mozilla/5.0 (ArchiveTune)")
                    .get()
                    .build()
            runCatching {
                client.newCall(probe).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag("AmazonAudio").w("Amazon stream rejected: %d", response.code)
                        return@use null
                    }
                    val contentLength = response.body?.contentLength()?.takeIf { it > 0 }
                    DirectStream(
                        uri = streamUrl,
                        mimeType = "audio/flac",
                        codecs = "flac",
                        contentLength = contentLength,
                        label = "Amazon Music $quality",
                        source = AudioSourceType.AMAZON,
                    )
                }
            }.getOrElse {
                Timber.tag("AmazonAudio").w(it, "Amazon resolution error")
                null
            }
        }
}
